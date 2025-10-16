package fortlev;
import robocode.*;
import robocode.AdvancedRobot;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.ScannedRobotEvent;
import robocode.Robot;
import static robocode.util.Utils.normalRelativeAngleDegrees;

import java.awt.*;
//import java.awt.Color;

/**
 * AndersonSilva - a robot by (Arthur Abdala, Arthur de Oliveira, Mateus Raffaelli e Matheus Posada)
 */

public class AndersonSilva extends AdvancedRobot {

	/**
	 * run: AndersonSilva's default behavior
	 */
	boolean peek;
	int count = 0; // Keeps track of how long we've
	// been searching for our target
	double gunTurnAmt; // How much to turn our gun when searching
	String trackName; // Name of the robot we're currently tracking



	public void run() {		
		setBodyColor(Color.black);
		setGunColor(Color.white);
		setRadarColor(Color.black);
		setScanColor(Color.red);
		
		// Identifica o tamanho da areana
		double largura = getBattleFieldWidth();  // Retorna a largura da arena 
		double altura = getBattleFieldHeight(); // Retorna a altura da arena 
		
		// Identifica a posição na arena
		double eixoX = getX(); // Retorna a coordenada X atual 
		double eixoY = getY(); // Retorna a coordenada Y atual 
	
		double passo = 1.0;       // quanto andamos a cada iteração
        double incremento = 0.08; // quanto aumenta o passo (raio) por iteração
        double angTurn = 3.0;     // quanto gira a cada iteração (graus) 
		boolean expandindo = true; // controla se o raio está abrindo ou fechando		

        setAdjustGunForRobotTurn(true); // separa giro do canhão
		setAdjustRadarForGunTurn(true);		

        while (true) {
			// movimento contínuo
            setTurnRight(angTurn);
            setAhead(passo);
            execute();    
			
			// Alterna entre abrir e fechar o espiral
			if (expandindo){       
            	passo = passo + incremento;  // aumenta o raio lentamente           
            	if (passo > 150) expandindo = false; // ponto máximo do raio
			} else {
				passo = passo - incremento; // diminui o raio lentamente
				if (passo < 10) {
					expandindo = true; // começa a abrir de novo
					// move o centro do espiral: se movimenta pelo mapa
					turnRight(45);
					ahead(100);
				}
		
		}
		
		// evitar encostar nas paredes
		if (getX() < 100 || getX() > getBattleFieldWidth() - 100 || getY() < 100 || getY() > getBattleFieldHeight() - 100) {
                turnRight(90);
				ahead(150);
		}}
	}


	public void onScannedRobot(ScannedRobotEvent e) {
		fire(4);
		// Note that scan is called automatically when the robot is moving.
		// By calling it manually here, we make sure we generate another scan event if there's a robot on the next
		// wall, so that we do not start moving up it until it's gone.
		if (peek) {
			scan();
		}
		
	}
	public void onHitWall(HitWallEvent e) {
    double bearing = e.getBearing();
    out.println("Bati na parede com ângulo: " + bearing);
    turnRight(-bearing); // Gira para o lado oposto da colisão
    ahead(100);          // Anda para frente após o giro
	}

	public void onWin(WinEvent e) {
		turnRight(36000);
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
