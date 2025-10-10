package fortlev;
import robocode.*;
import robocode.AdvancedRobot;
import robocode.HitRobotEvent;
import robocode.ScannedRobotEvent;
import robocode.Robot;

import java.awt.*;
//import java.awt.Color;

// API help : https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html

/**
 * AndersonSilva - a robot by (Arthur de Oliveira, Mateus Raffaelli e Matheus Posada)
 */
public class AndersonSilva extends AdvancedRobot {

	/**
	 * run: AndersonSilva's default behavior
	 */
	boolean peek;


/**
 * SpinBot - a sample robot by Mathew Nelson.
 * <p>
 * Moves in a circle, firing hard when an enemy is detected.
 *
 * @author Mathew A. Nelson (original)
 * @author Flemming N. Larsen (contributor)
 */


	/**
	 * SpinBot's run method - Circle
	 */
	public void run() {
		// Set colors
		setBodyColor(Color.blue);
		setGunColor(Color.blue);
		setRadarColor(Color.black);
		setScanColor(Color.yellow);

		// Loop forever
		while (true) {
			// Tell the game that when we take move,
			// we'll also want to turn right... a lot.
			setTurnRight(10000);
			// Limit our speed to 5
			setMaxVelocity(5);
			// Start moving (and turning)
			ahead(10000);
			// Repeat.
		}
	}

	/**
	 * onScannedRobot: Fire hard!
	 */
	
	/**
	 * onHitRobot:  If it's our fault, we'll stop turning and moving,
	 * so we need to turn again to keep spinning.
	 */
	
	public void onScannedRobot(ScannedRobotEvent e) {
		fire(4);
		// Note that scan is called automatically when the robot is moving.
		// By calling it manually here, we make sure we generate another scan event if there's a robot on the next
		// wall, so that we do not start moving up it until it's gone.
		if (peek) {
			scan();
		}
	}
	public void onHitRobot(HitRobotEvent e) {
		if (e.getBearing() > -10 && e.getBearing() < 10) {
			fire(3);
		}
		if (e.isMyFault()) {
			turnRight(10);
		}
	}
}