package com.meshiest.eventnetwork.server;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.JPanel;

/**
 * A panel that displays frequencies as a line graph
 * @author Meshiest
 * @since 20161124
 * @version 0.0.3
 */
@SuppressWarnings("serial")
public class RequestInfoPanel extends JPanel implements Runnable {

  /**
   * Number of integers to store
   */
  public static final int HISTORY_LENGTH = 40;
  
  /**
   * Number of lines to draw for each lerp
   */
  public static final int LERP_QUALITY = 5;

  /**
   * Previous requests per second
   */
  private ArrayList<Integer> history;
   
  /**
   * Current number of requests in the last second
   */
  private int numReqs;
  
  /**
   * The last time the graph updated
   */
  long lastUpdate;
  
  /**
   * Default constructor, creates a panel that displays requests per second
   */
  public RequestInfoPanel() {
    numReqs = 0;
    history = new ArrayList<>();
    lastUpdate = 0;
    this.setPreferredSize(new Dimension(0, 100));
    
    new Thread(this).start();
  }
  
  /**
   * Called to append the current number of requests to the history
   */
  private void updateHistory() {
    history.add(numReqs);
    
    if(history.size() > HISTORY_LENGTH + 1)
      history.remove(0);
    
    numReqs = 0;
    repaint();
  }
  
  /**
   * Increment the current number of requests
   */
  public void inc() {
    numReqs ++;
  }
  
  /**
   * Renders the graph
   */
  @Override
  public void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics;
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    
    int width = getWidth(), height = getHeight();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);
   
    
    int max = Collections.max(history);
    double partX = 1.0 * width / HISTORY_LENGTH;
    double partY = 1.0 * height / max;
    g.setColor(Color.BLACK);
    
    g.drawString("Inbound Requests Per Second", 10, height - 10);
    
    double shift = 0;
    if(history.size() < HISTORY_LENGTH + 1) {
      shift = partX * (HISTORY_LENGTH - history.size() + 1);
      g.translate(shift, 0);
    }
    
    Point mouse = getMousePosition();
    if(mouse == null)
      mouse = new Point(-100, -100);
    
    for(int i = 0; i < history.size(); i++) {
      
      int curr = history.get(i);
      int next = i == history.size() - 1 ? 0 : history.get(i+1);
      int y = (int)(lerp(curr, next, 0) * partY);
      int x = (int)(i * partX);
      
      // If the mouse is in the same column as the value, show the value on the mouse
      if(Math.abs(x - mouse.x + shift) <= partX/2) {
        g.drawOval(x - 10, height - y - 10, 20, 20);
        g.drawString(curr+"", mouse.x - (int)shift, mouse.y);
      }
      
      // draw a smooth curved line between values
      for(int j = 1; j <= LERP_QUALITY; j++) {
        int y2 = (int)(lerp(curr, next, j * 1.0 / LERP_QUALITY) * partY);
        int x2 = (int)(i * partX + partX / LERP_QUALITY * j);
        g.drawLine(x, height - y, x2, height - y2);
        x = x2;
        y = y2;
      }
      
    }
  }
  
  /**
   * Linear interpolation function
   * @param a Starting value
   * @param b Ending Value
   * @param t Distance from start to end in %, 0-1
   * @return Linear interpolated position between a and b
   */
  public static double lerp(int a, int b, double t) {
    double part = Math.cos(t * Math.PI) * 0.5 + 0.5;
    return a * part + b * (1-part);
  }
  
  /**
   * Runnable method, updates history every second
   */
  @Override
  public void run() {
    while(true) {
      try {
        long now = System.currentTimeMillis();
        if(now - lastUpdate > 1000) {
          updateHistory();
          lastUpdate = now;
        }
        repaint();
        Thread.currentThread();
        Thread.sleep(30);
      }catch (Exception e) {
      }
    }
    
  }


}
