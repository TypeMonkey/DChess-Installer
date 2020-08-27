package jg.chess.install.chesspieces;

import java.io.InputStream;

public class ChessPiecesGrabber {

  public static InputStream grab(String resourceName) {
    return ChessPiecesGrabber.class.getResourceAsStream(resourceName);
  }
  
}
