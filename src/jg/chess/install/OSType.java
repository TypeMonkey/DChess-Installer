package jg.chess.install;

import org.apache.commons.lang3.SystemUtils;

/**
 * List of supported OS's for the DChess client, all 64-bit.
 * 
 * Each OS type has an attached JDK archive download link (Amazon Corretto 8)
 * 
 * Supported Linux version is x86-64
 * 
 * @author Jose
 *
 */
public enum OSType {
  LINUX("https://corretto.aws/downloads/latest/amazon-corretto-8-x64-linux-jdk.tar.gz", "tar.gz", "linux64"),
  WINDOWS("https://corretto.aws/downloads/latest/amazon-corretto-8-x64-windows-jdk.zip", "zip", "windows64"),
  MAC("https://corretto.aws/downloads/latest/amazon-corretto-8-x64-macos-jdk.tar.gz", "tar.gz", "mac");
  
  private final String jdk8Link;
  private final String archiveType;
  private final String osFullName;
  
  private OSType(String jdk8Link, String archiveType, String osFullName) {
    this.jdk8Link = jdk8Link;
    this.archiveType = archiveType;
    this.osFullName = osFullName;
  }
  
  public String getArchiveType() {
    return archiveType;
  }
  
  public String getJdk8Link() {
    return jdk8Link;
  }
  
  public String getPlatform() {
    return osFullName;
  }
  
  /**
   * Returns the OSType based on the machine's OS
   * @return the OSType based on the machine's OS, or null is OS cannot be detected or is unsupported
   */
  public static OSType determineOS() {
    if (SystemUtils.IS_OS_WINDOWS) {
      return WINDOWS;
    }
    else if (SystemUtils.IS_OS_MAC) {
      return MAC;
    }
    else if(SystemUtils.IS_OS_LINUX){
      return LINUX;
    }
    else {
      return null;
    }
  }
}
