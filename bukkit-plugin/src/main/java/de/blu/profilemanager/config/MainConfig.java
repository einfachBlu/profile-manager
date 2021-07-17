package de.blu.profilemanager.config;

import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Singleton
public final class MainConfig {

  @Inject private JavaPlugin plugin;

  @Getter private String serviceUrl = "http://localhost:8080";

  private File configFile;
  private YamlConfiguration config;

  public void init() {
    // Init Config
    this.configFile = new File(this.plugin.getDataFolder(), "config.yml");

    this.plugin.getDataFolder().mkdirs();
    if (!this.configFile.exists()) {
      try {
        this.configFile.createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
        return;
      }
    }

    this.config = YamlConfiguration.loadConfiguration(this.configFile);

    // Validate
    Map<String, Object> defaultValues = new HashMap<>();

    defaultValues.put("serviceUrl", this.serviceUrl);

    for (String key : defaultValues.keySet()) {
      if (!this.config.contains(key)) {
        this.config.set(key, defaultValues.get(key));
        this.save();
      }
    }

    // Load Config
    this.load();
  }

  private void load() {
    this.serviceUrl = this.config.getString("serviceUrl");

    boolean changed = false;
    while (this.serviceUrl.length() > 0 && this.serviceUrl.endsWith("/")) {
      this.serviceUrl = this.serviceUrl.substring(0, this.serviceUrl.length() - 1);
      changed = true;
    }

    if (changed) {
      this.config.set("serviceUrl", this.serviceUrl);
      this.save();
    }
  }

  private void save() {
    try {
      this.config.save(configFile);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
