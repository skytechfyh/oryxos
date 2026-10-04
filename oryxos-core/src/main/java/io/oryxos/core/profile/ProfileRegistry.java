package io.oryxos.core.profile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profile 的内存索引,按 name 查找。
 *
 * <p>本节只有启动扫描这一条注册路径,所以 {@link #register} 仅包内可见;运行时注册由第 29 节补。
 */
public class ProfileRegistry {

  private final Map<String, Profile> profiles = new ConcurrentHashMap<>();

  /** 同名已存在时拒绝覆盖。 */
  boolean register(Profile profile) {
    return profiles.putIfAbsent(profile.name(), profile) == null;
  }

  public Optional<Profile> find(String name) {
    return Optional.ofNullable(profiles.get(name));
  }

  public Collection<Profile> all() {
    return List.copyOf(profiles.values());
  }
}
