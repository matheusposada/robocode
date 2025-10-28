<<<<<<< HEAD
package teste;
import robocode.*;
//import java.awt.Color;

// API help : https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html

/**
 * FirstRobot - a robot by (your name here)
 */
public class FirstRobot extends Robot
{
	/**
	 * run: FirstRobot's default behavior
	 */
	public void run() {
		// Initialization of the robot should be put here

		// After trying out your robot, try uncommenting the import at the top,
		// and the next line:

		// setColors(Color.red,Color.blue,Color.green); // body,gun,radar

		// Robot main loop
		while(true) {
			// Replace the next 4 lines with any behavior you would like
			ahead(90);
			turnGunRight(360);
			back(100);
			turnGunRight(360);
		}
	}

	/**
	 * onScannedRobot: What to do when you see another robot
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
		// Replace the next line with any behavior you would like
		fire(1);
	}

	/**
	 * onHitByBullet: What to do when you're hit by a bullet
	 */
	public void onHitByBullet(HitByBulletEvent e) {
		// Replace the next line with any behavior you would like
		back(50);
	}
	
	/**
	 * onHitWall: What to do when you hit a wall
	 */
	public void onHitWall(HitWallEvent e) {
		// Replace the next line with any behavior you would like
		back(15);
	}	
=======
package fortlev;

import robocode.*;
import static robocode.util.Utils.normalRelativeAngleDegrees;
import java.awt.*;
import java.awt.geom.Point2D;

public class FirstRobot extends AdvancedRobot {

    // ==========================
    // VARIÁVEIS DE CONTROLE
    // ==========================
    double passo = 1.0;           
    double incremento = 0.08;     
    double angTurn = 3.0;         
    boolean expandindo = true;    
    int count = 0;                
    double gunTurnAmt;            
    String trackName;             
    boolean wantToFire = false;   
    double lastFirePower = 3;     
    int direcaoMovimento = 1;    
    long ultimoTempoMudancaDirecao = 0;

    // ==========================
    // VARIÁVEIS DO INIMIGO
    // ==========================
    double enemyVelocity = 0;
    double enemyHeading = 0;
    double enemyDistance = 0;
    double enemyBearing = 0;

    // ==========================
    // HISTÓRICO PARA ARMA (movimentação evasiva)
    // ==========================
    final int HIST_SIZE = 5;       
    double[] histHeading = new double[HIST_SIZE]; 
    double[] histVelocity = new double[HIST_SIZE]; 
    double[] histErroX = new double[HIST_SIZE]; 
    double[] histErroY = new double[HIST_SIZE]; 
    int histIndex = 0;

    // ==========================
    // MÉTODO PRINCIPAL
    // ==========================
    public void run() {
        // ===== CONFIGURAÇÃO DE CORES =====
        setBodyColor(Color.black);
        setGunColor(Color.white);
        setRadarColor(Color.white);
        setScanColor(Color.red);
        setBulletColor(Color.yellow);

        // Permite radar e canhão independentes do corpo
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        trackName = null;
        gunTurnAmt = 10;

        // ===== LOOP PRINCIPAL =====
        while (true) {
            evitarColisoes();     // Evita paredes e inimigos
            movimentoEspiral();  // Movimento base em espiral

            if (trackName == null) {
                setTurnRadarRight(360); // Varre campo
                setTurnGunRight(gunTurnAmt);
            }

            disparo(); // Dispara quando alinhado

            if (getTime() - ultimoTempoMudancaDirecao > 15 && trackName != null) {
                movimentoARMA();  // Movimentação evasiva ARMA
                ultimoTempoMudancaDirecao = getTime();
            }

            execute();
        }
    }

    // ==========================
    // EVITAR COLISÕES (paredes e inimigos)
    // ==========================
    void evitarColisoes() {
        double margem = 60; 
        boolean pertoParede = false;

        if (getX() < margem || getX() > getBattleFieldWidth() - margem ||
            getY() < margem || getY() > getBattleFieldHeight() - margem) {
            pertoParede = true;
        }

        if (pertoParede) {
            // Calcula ângulo para o centro do campo
            double anguloCentro = Math.toDegrees(Math.atan2(
                getBattleFieldWidth()/2 - getX(),
                getBattleFieldHeight()/2 - getY()
            ));

            // Inverte a direção de movimento e gira para o centro
            direcaoMovimento *= -1;
            setTurnRight(normalRelativeAngleDegrees(anguloCentro - getHeading()));
            setAhead(100 * direcaoMovimento);  
        }
    }

    // ==========================
    // MOVIMENTO EM ESPIRAL
    // ==========================
    void movimentoEspiral() {
        if (trackName != null && enemyDistance < 300) {
            angTurn = 5.0;
            incremento = 0.15;
        } else { 
            angTurn = 3.0;
            incremento = 0.08;
        }

        setTurnRight(angTurn * direcaoMovimento);
        setAhead(passo * direcaoMovimento);

        if (expandindo) {
            passo += incremento;
            if (passo > 150) expandindo = false;
        } else {
            passo -= incremento;
            if (passo < 10) {
                expandindo = true;
                setTurnRight(45);
                setAhead(100);
            }
        }
    }

    // ==========================
    // DISPARO
    // ==========================
    void disparo() {
        double gunRemaining = Math.abs(getGunTurnRemaining());
        if (trackName != null && wantToFire && gunRemaining <= 10 && getGunHeat() == 0) {
            fire(lastFirePower);
            wantToFire = false;
        }
    }

    // ==========================
    // MOVIMENTAÇÃO EVASIVA ARMA
    // ==========================
    void movimentoARMA() {
        double sumHeading = 0, sumVelocity = 0, sumErroX = 0, sumErroY = 0;
        for (int i = 0; i < HIST_SIZE; i++) {
            sumHeading += histHeading[i];
            sumVelocity += histVelocity[i];
            sumErroX += histErroX[i];
            sumErroY += histErroY[i];
        }

        double headingPrevisto = sumHeading / HIST_SIZE;
        double velocityPrevisto = sumVelocity / HIST_SIZE;
        double erroXPrevisto = sumErroX / HIST_SIZE;
        double erroYPrevisto = sumErroY / HIST_SIZE;

        double futuroX = getX() + Math.sin(Math.toRadians(headingPrevisto)) * velocityPrevisto * 5 + erroXPrevisto;
        double futuroY = getY() + Math.cos(Math.toRadians(headingPrevisto)) * velocityPrevisto * 5 + erroYPrevisto;

        // Limita posição futura para não bater na parede
        futuroX = Math.max(Math.min(futuroX, getBattleFieldWidth() - 20), 20);
        futuroY = Math.max(Math.min(futuroY, getBattleFieldHeight() - 20), 20);

        double anguloEvitar = Math.toDegrees(Math.atan2(futuroX - getX(), futuroY - getY())) + 90;
        setTurnRight(normalRelativeAngleDegrees(anguloEvitar - getHeading()));

        double vel = 2 + Math.random() * 4;

        // Verifica se o avanço ultrapassa limites; inverte se necessário
        double projX = getX() + Math.sin(Math.toRadians(getHeading())) * vel * direcaoMovimento;
        double projY = getY() + Math.cos(Math.toRadians(getHeading())) * vel * direcaoMovimento;
        if (projX < 20 || projX > getBattleFieldWidth() - 20 ||
            projY < 20 || projY > getBattleFieldHeight() - 20) {
            direcaoMovimento *= -1;
        }

        setAhead(vel * direcaoMovimento);
    }

    // ==========================
    // PREDIÇÃO DE TIRO
    // ==========================
    public double calcularAnguloPreditivo() {
        double bulletSpeed = 20 - 3 * lastFirePower;
        double myX = getX();
        double myY = getY();
        double anguloAbsoluto = getHeading() + enemyBearing;
        double enemyX = myX + Math.sin(Math.toRadians(anguloAbsoluto)) * enemyDistance;
        double enemyY = myY + Math.cos(Math.toRadians(anguloAbsoluto)) * enemyDistance;

        double headingRad = Math.toRadians(enemyHeading);
        double velocity = enemyVelocity;
        double futuroX = enemyX;
        double futuroY = enemyY;

        for (double t = 1; t * bulletSpeed < Point2D.distance(myX, myY, futuroX, futuroY); t++) {
            futuroX += Math.sin(headingRad) * velocity;
            futuroY += Math.cos(headingRad) * velocity;
            futuroX = Math.max(Math.min(futuroX, getBattleFieldWidth() - 18), 18);
            futuroY = Math.max(Math.min(futuroY, getBattleFieldHeight() - 18), 18);
        }

        double angulo = Math.toDegrees(Math.atan2(futuroX - myX, futuroY - myY));
        return normalRelativeAngleDegrees(angulo - getGunHeading());
    }

    // ==========================
    // EVENTO: DETECTA INIMIGO
    // ==========================
    public void onScannedRobot(ScannedRobotEvent e) {
        if (trackName != null && !e.getName().equals(trackName)) return;
        if (trackName == null) trackName = e.getName();
        count = 0;

        enemyVelocity = e.getVelocity();
        enemyHeading = e.getHeading();
        enemyDistance = e.getDistance();
        enemyBearing = e.getBearing();

        histHeading[histIndex] = e.getHeading();
        histVelocity[histIndex] = e.getVelocity();

        double myX = getX();
        double myY = getY();
        double absAng = getHeading() + e.getBearing();
        double ex = myX + Math.sin(Math.toRadians(absAng)) * enemyDistance;
        double ey = myY + Math.cos(Math.toRadians(absAng)) * enemyDistance;
        histErroX[histIndex] = ex - (myX + Math.sin(Math.toRadians(enemyHeading)) * enemyVelocity);
        histErroY[histIndex] = ey - (myY + Math.cos(Math.toRadians(enemyHeading)) * enemyVelocity);

        histIndex = (histIndex + 1) % HIST_SIZE;

        double radarTurn = normalRelativeAngleDegrees(getHeading() + e.getBearing() - getRadarHeading());
        radarTurn += radarTurn > 0 ? 20 : -20;
        setTurnRadarRight(radarTurn);

        setTurnGunRight(calcularAnguloPreditivo());

        if (e.getDistance() > 200) lastFirePower = 1.5;
        else if (e.getDistance() > 100) lastFirePower = 2;
        else lastFirePower = 3;

        wantToFire = true;
    }

    // ==========================
    // EVENTO: INIMIGO MORREU
    // ==========================
    public void onRobotDeath(RobotDeathEvent e) {
        if (trackName != null && e.getName().equals(trackName)) {
            trackName = null;
            wantToFire = false;
        }
    }

    // ==========================
    // EVENTOS DE COLISÃO
    // ==========================
    public void onHitWall(HitWallEvent e) {
        direcaoMovimento *= -1;
        setAhead(100 * direcaoMovimento);
        setTurnRight(90 - e.getBearing());
        passo = 10;
        expandindo = true;
        execute();
    }

    public void onHitRobot(HitRobotEvent e) {
        direcaoMovimento *= -1;
        setAhead(50 * direcaoMovimento);
        double gunTurn = normalRelativeAngleDegrees(getHeading() + e.getBearing() - getGunHeading());
        setTurnGunRight(gunTurn);
        if (getGunHeat() == 0) setFire(2);
    }

    // ==========================
    // EVENTO: VENCE A PARTIDA
    // ==========================
    public void onWin(WinEvent e) {
        for (int i = 0; i < 20; i++) {
            turnRight(30);
            turnLeft(30);
        }
    }
>>>>>>> 38cd8de (Teste de do modelo ARMA para comparacao com o original)
}
