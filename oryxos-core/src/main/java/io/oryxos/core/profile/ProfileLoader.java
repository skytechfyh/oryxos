package io.oryxos.core.profile;

import io.oryxos.core.profile.Profile.Identity;
import io.oryxos.core.profile.Profile.ProviderRef;
import io.oryxos.core.profile.Profile.Settings;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 启动时扫描 Profile 目录,逐个解析、校验并注册到 {@link ProfileRegistry}。
 *
 * <p>坏的 Profile 只记错误日志、不阻断启动,其余照常加载。本节只校验"provider 名在全局层存在"。
 */
public class ProfileLoader {

  private static final Logger LOG = LoggerFactory.getLogger(ProfileLoader.class);

  private final Path dir;
  private final Set<String> knownProviders;
  private final Function<String, String> env;
  private final ProfileRegistry registry;

  /**
   * @param dir Profile 目录,通常是 {@code .oryxos/profiles/}
   * @param knownProviders 全局层已接入的 provider 名
   * @param env 环境变量查询函数,生产传 {@code System::getenv},测试注入替身
   */
  public ProfileLoader(
      Path dir,
      Set<String> knownProviders,
      Function<String, String> env,
      ProfileRegistry registry) {
    this.dir = dir;
    this.knownProviders = Set.copyOf(knownProviders);
    this.env = env;
    this.registry = registry;
  }

  /** 扫描并注册,返回成功加载的数量。目录不存在视为空。 */
  public int load() {
    if (!Files.isDirectory(dir)) {
      LOG.info("Profile 目录不存在,跳过加载: {}", dir);
      return 0;
    }
    List<Path> files;
    try (Stream<Path> stream = Files.list(dir)) {
      files = stream.filter(ProfileLoader::isYaml).sorted().toList();
    } catch (IOException e) {
      LOG.error("扫描 Profile 目录失败: {}", dir, e);
      return 0;
    }
    int loaded = 0;
    for (Path file : files) {
      if (loadOne(file)) {
        loaded++;
      }
    }
    LOG.info("Profile 加载完成: 成功 {} / 共 {}", loaded, files.size());
    return loaded;
  }

  private boolean loadOne(Path file) {
    try {
      Profile profile = parse(file);
      validate(profile);
      if (!registry.register(profile)) {
        LOG.error("Profile 加载失败 {}: name 重复 {},保留先加载的一份", file.getFileName(), profile.name());
        return false;
      }
      return true;
    } catch (IOException | RuntimeException e) {
      // 坏文件不能阻断其余加载,但必须留下清晰的错误日志
      LOG.error("Profile 加载失败 {}: {}", file.getFileName(), e.getMessage(), e);
      return false;
    }
  }

  private Profile parse(Path file) throws IOException {
    Object root;
    // SafeConstructor 只构造基本类型,避免 YAML 反序列化攻击
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      root = yaml.load(reader);
    }
    if (!(root instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("文件内容不是 YAML 映射");
    }
    return toProfile(asMap(resolve(map)));
  }

  private Profile toProfile(Map<String, Object> m) {
    Map<String, Object> identity = asMap(m.get("identity"));
    Map<String, Object> provider = asMap(m.get("provider"));
    Map<String, Object> settings = asMap(m.get("settings"));
    return new Profile(
        text(m.get("name")),
        text(m.get("description")),
        new Identity(text(identity.get("agent_name")), text(identity.get("prompt"))),
        new ProviderRef(
            text(provider.get("name")),
            text(provider.get("model")),
            decimal(provider.get("temperature"))),
        strings(m.get("tools")),
        strings(m.get("skills")),
        strings(m.get("mcp_servers")),
        strings(m.get("channels")),
        strings(m.get("notify_channels")),
        maps(m.get("schedules")),
        strings(m.get("bootstrap")),
        new Settings(
            integer(settings.get("max_iterations")), integer(settings.get("max_history_turns"))));
  }

  private void validate(Profile profile) {
    if (profile.name() == null || profile.name().isBlank()) {
      throw new IllegalArgumentException("缺少必填字段 name");
    }
    String provider = profile.provider().name();
    if (provider == null || provider.isBlank()) {
      throw new IllegalArgumentException("Profile " + profile.name() + " 缺少 provider.name");
    }
    if (!knownProviders.contains(provider)) {
      throw new IllegalArgumentException(
          "Profile "
              + profile.name()
              + " 引用的 provider 不存在: "
              + provider
              + ",全局层已接入: "
              + knownProviders);
    }
  }

  /** 递归替换所有字符串里的 ${ENV} 占位。 */
  private Object resolve(Object value) {
    if (value instanceof String s) {
      return substitute(s);
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      map.forEach((k, v) -> out.put(String.valueOf(k), resolve(v)));
      return out;
    }
    if (value instanceof List<?> list) {
      List<Object> out = new ArrayList<>();
      list.forEach(v -> out.add(resolve(v)));
      return out;
    }
    return value;
  }

  /** 替换环境变量占位(变量名可带冒号后的默认值)。手工解析而不用正则,避免回溯型 ReDoS;没有闭合花括号的占位起始符原样保留。 */
  private String substitute(String raw) {
    StringBuilder out = new StringBuilder();
    int from = 0;
    while (true) {
      int start = raw.indexOf("${", from);
      int end = start < 0 ? -1 : raw.indexOf('}', start);
      if (end < 0) {
        out.append(raw, from, raw.length());
        return out.toString();
      }
      out.append(raw, from, start);
      String body = raw.substring(start + 2, end);
      int colon = body.indexOf(':');
      String name = colon < 0 ? body : body.substring(0, colon);
      String value = env.apply(name);
      if (value == null && colon >= 0) {
        value = body.substring(colon + 1);
      }
      if (value == null) {
        throw new IllegalArgumentException("环境变量未设置且无默认值: " + name);
      }
      out.append(value);
      from = end + 1;
    }
  }

  private static boolean isYaml(Path path) {
    Path fileName = path.getFileName();
    if (fileName == null || !Files.isRegularFile(path)) {
      return false;
    }
    String name = fileName.toString();
    return name.endsWith(".yaml") || name.endsWith(".yml");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }

  private static String text(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static Double decimal(Object value) {
    return value instanceof Number n ? n.doubleValue() : null;
  }

  private static Integer integer(Object value) {
    return value instanceof Number n ? n.intValue() : null;
  }

  private static List<String> strings(Object value) {
    if (value instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    return List.of();
  }

  private static List<Map<String, Object>> maps(Object value) {
    if (value instanceof List<?> list) {
      return list.stream().map(ProfileLoader::asMap).toList();
    }
    return List.of();
  }
}
