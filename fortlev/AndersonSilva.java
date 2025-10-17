package fortlev;

import robocode.*;
import static robocode.util.Utils.normalRelativeAngleDegrees;
import java.awt.*;

/**
 * AndersonSilva - a robot by (Arthur Abdala, Arthur de Oliveira, Mateus Raffaelli e Matheus Posada)
 */
public class AndersonSilva extends AdvancedRobot {

    // ===== VARIÁVEIS DE CONTROLE =====
    double velocidadeRadar = 30; // velocidade de rotação do radar (em graus por tick)
    boolean girandoDireita = true; // controla o sentido de rotação do radar
    double passo = 1.0;            // distância que o robô anda a cada iteração (controla o raio da espiral)
    double incremento = 0.08;      // quanto o "passo" aumenta ou diminui (abre/fecha a espiral)
    double angTurn = 3.0;          // ângulo que o corpo gira por ciclo
    boolean expandindo = true;     // indica se a espiral está abrindo (true) ou fechando (false)

    // ===== MÉTODO PRINCIPAL =====
    public void run() {
        // === CONFIGURAÇÃO DE CORES ===
        setBodyColor(Color.black);
        setGunColor(Color.white);
        setRadarColor(Color.white);
        setScanColor(Color.red);

        // Permite que radar e canhão girem de forma independente do corpo
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // === LOOP PRINCIPAL (executa até o fim da partida) ===
        while (true) {
            // === MOVIMENTO EM ESPIRAL CONTÍNUO ===
            setTurnRight(angTurn);  // gira levemente o corpo
            setAhead(passo);        // anda para frente (passo define o raio da espiral)

            // Alterna entre expandir e contrair a espiral
            if (expandindo) {
                passo = passo + incremento;            // aumenta o raio
                if (passo > 150) expandindo = false; // muda para fase de contração
            } else {
                passo = passo - incremento;            // diminui o raio
                if (passo < 10) {
                    expandindo = true;          // volta a expandir
                    setTurnRight(45);           // muda o centro da espiral
                    setAhead(100);              // avança um pouco para deslocar o padrão no mapa
                }
            }

            // === EVITAR PAREDES ===
            // Se o robô estiver próximo das bordas do campo de batalha, muda de direção
            if (getX() < 100 || getX() > getBattleFieldWidth() - 100 ||
                getY() < 100 || getY() > getBattleFieldHeight() - 100) {
                setTurnRight(90);
                setAhead(150);
            }

            // === CONTROLE DO RADAR ===
            // Gira o radar continuamente para procurar inimigos
            if (girandoDireita)
                setTurnRadarRight(velocidadeRadar);
            else
                setTurnRadarLeft(velocidadeRadar);

            // A cada 50 "ticks", muda o sentido e a velocidade do radar para dar dinamismo
            if (getTime() % 50 == 0) {
                girandoDireita = !girandoDireita;
                velocidadeRadar = 5 + Math.random() * 40; // velocidade aleatória entre 5° e 45°
            }

            execute(); // executa todos os comandos pendentes sem travar o loop
        }
    }

    // ===== EVENTO: QUANDO DETECTA UM INIMIGO =====
    public void onScannedRobot(ScannedRobotEvent e) {
        // Calcula quanto o radar e o canhão precisam girar para mirar no inimigo
        double radarTurn = normalRelativeAngleDegrees(getHeading() + e.getBearing() - getRadarHeading());
        double gunTurn = normalRelativeAngleDegrees(getHeading() + e.getBearing() - getGunHeading());

        // Ajusta o radar e o canhão na direção do inimigo (sem bloquear o movimento)
        setTurnRadarRight(radarTurn);
        setTurnGunRight(gunTurn);

        // Atira continuamente enquanto o canhão estiver pronto (sem parar o robô)
        if (getGunHeat() == 0) {
            setFire(2.5); // intensidade do tiro (0.1 a 3)
        }

        // Mantém o radar girando mesmo após detectar o inimigo
        if (Math.abs(radarTurn) < 5)
            setTurnRadarRight(30);
    }

    // ===== EVENTO: QUANDO BATE NA PAREDE =====
    public void onHitWall(HitWallEvent e) {
        double bearing = e.getBearing(); // ângulo em que bateu
        setTurnRight(-bearing);          // vira para o lado oposto
        setAhead(100);                   // se afasta da parede
    }

    // ===== EVENTO: QUANDO BATE EM OUTRO ROBÔ =====
    public void onHitRobot(HitRobotEvent e) {
        // Se o inimigo estiver bem à frente, dispara
        // Calcula o ângulo que o canhão precisa girar para mirar no inimigo
   		 double gunTurn = normalRelativeAngleDegrees(getHeading() + e.getBearing() - getGunHeading());

    	// Gira o canhão em direção ao inimigo (sem bloquear o movimento)
    	setTurnGunRight(gunTurn);

   	 	// Atira após alinhar o canhão
   		 if (getGunHeat() == 0) {
        	setFire(3); // potência do tiro
    	}

   		 // Se a colisão foi culpa do nosso robô, desvia um pouco para não travar
    	if (e.isMyFault()) {
        	setTurnRight(10);
        }
    }

    // ===== EVENTO: QUANDO VENCE A PARTIDA =====
    public void onWin(WinEvent e) {
        // Faz uma "dança" girando várias vezes em comemoração
        for (int i = 0; i < 20; i++) {
            turnRight(360);
        }
    }
}