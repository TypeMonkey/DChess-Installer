package jg.chess.install.xmls;

import java.io.InputStream;

public class FXMLGrabber {

  public static InputStream grab(String resourceName) {
    return FXMLGrabber.class.getResourceAsStream(resourceName);
  }
  
}
