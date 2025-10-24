package fortlev;

import robocode.*;
import static robocode.util.Utils.normalRelativeAngleDegrees;
import java.awt.*;
import robocode.HitWallEvent;
import robocode.HitRobotEvent;
import java.awt.geom.Point2D;


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
    int count = 0;
    double gunTurnAmt;
    String trackName;
    boolean wantToFire = false;
    double lastFirePower = 3;
    int direcaoMovimento = 1;  // 1 = frente, -1 = trás (para esquiva)
	long ultimoTempoMudancaDirecao = 0;

    // NOVAS VARIÁVEIS PARA PREDIÇÃO
    double enemyVelocity = 0;        // velocidade do inimigo
    double enemyHeading = 0;         // direção do inimigo
    double enemyDistance = 0;        // distância do inimigo
    double enemyBearing = 0;         // ângulo relativo ao inimigo

    // ===== MÉTODO PRINCIPAL =====
    public void run() {
        // === CONFIGURAÇÃO DE CORES ===
        setBodyColor(Color.black);
        setGunColor(Color.white);
        setRadarColor(Color.white);
        setScanColor(Color.red);
        setBulletColor(Color.yellow);

        // Permite que radar e canhão girem de forma independente do corpo
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        trackName = null;
        gunTurnAmt = 10;

        // === LOOP PRINCIPAL (executa até o fim da partida) ===
        while (true) {
            // === EVITAR PAREDES (CHECAR PRIMEIRO!) ===
            double margemSeguranca = 80;
            boolean pertoParede = false;
            
            if (getX() < margemSeguranca || 
                getX() > getBattleFieldWidth() - margemSeguranca ||
                getY() < margemSeguranca || 
                getY() > getBattleFieldHeight() - margemSeguranca) {
                
                pertoParede = true;
                
                // Calcula direção para o centro do campo
                double anguloParaCentro = Math.toDegrees(Math.atan2(
                    getBattleFieldWidth()/2 - getX(),
                    getBattleFieldHeight()/2 - getY()
                ));
                
                double virar = normalRelativeAngleDegrees(anguloParaCentro - getHeading());
                
                setTurnRight(virar);
                setAhead(150);
            }

            // === MOVIMENTO EM ESPIRAL CONTÍNUO ===
            if (!pertoParede) {
                // Se estiver próximo: espiral mais apertada e rápida
                if (trackName != null && enemyDistance < 300) {
                    angTurn = 5.0;
                    incremento = 0.15;
                } else { 
                    angTurn = 3.0;
                    incremento = 0.08;
                }
                
                setTurnRight(angTurn * direcaoMovimento);
                setAhead(passo * direcaoMovimento);

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
            }

            // === CONTROLE DO RADAR ===
            // Se NÃO temos alvo, varre em círculos
            if (trackName == null) {
                setTurnRadarRight(360);
                setTurnGunRight(gunTurnAmt);
            }
            // Se JÁ temos alvo, o radar será ajustado no onScannedRobot

            // ATIRAR COM CONDIÇÕES MAIS FLEXÍVEIS
            double gunRemaining = Math.abs(getGunTurnRemaining());
            double GUN_ANGLE_TOLERANCE = 10.0;  // Aumentado para 10 graus (mais tolerante)
            
            if (trackName != null && wantToFire && 
                gunRemaining <= GUN_ANGLE_TOLERANCE && 
                getGunHeat() == 0) {  //  Verifica se canhão está frio
                
                fire(lastFirePower);
                wantToFire = false;
            }

            // Lógica de "procura" de alvo usando count
            count++;

            if (count > 2) gunTurnAmt = -10;
            if (count > 5) gunTurnAmt = 10;
            if (count > 11) trackName = null;

            execute(); // Executa todos os comandos pendentes

            // Muda direção periodicamente para esquiva imprevisível
            if (getTime() - ultimoTempoMudancaDirecao > 50 && Math.random() > 0.7) {
                direcaoMovimento *= -1;
                ultimoTempoMudancaDirecao = getTime();
            }
        }
    }

    // ===== MÉTODO DE PREDIÇÃO DE TIRO =====
    /**
     * Calcula o ângulo para atirar prevendo onde o inimigo estará
     * Usa predição linear simples (assume que o inimigo continuará em linha reta)
     */
   		public double calcularAnguloPreditivo() {
        // Velocidade da bala baseada na potência do tiro
        double bulletSpeed = 20 - 3 * lastFirePower;
		//posicao atual do robo
 		double myX = getX();
    	double myY = getY();
               
        // Posição atual do inimigo em coordenadas absolutas
    	double anguloAbsolutoInimigo = getHeading() + enemyBearing;
    	double enemyX = myX + Math.sin(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
    	double enemyY = myY + Math.cos(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;;
		
		//velocidade e direcao do adversario
		double headingRad = Math.toRadians(enemyHeading); // direção atual do inimigo em radianos
    	double velocity = enemyVelocity;                  // velocidade atual do inimigo
	
		// ===== INICIALIZAÇÃO DA POSIÇÃO FUTURA =====
    	double futuroX = enemyX;
    	double futuroY = enemyY;
		   
        // Simula o movimento do inimigo por ticks até a bala alcançar
   		double deltaTime = 1; // passo de 1 tick
    	for (double t = 0; t * bulletSpeed < Point2D.distance(myX, myY, futuroX, futuroY); t += deltaTime) {
        	// Atualiza posição futura com base na velocidade e direção do inimigo
        	futuroX += Math.sin(headingRad) * velocity * deltaTime;
        	futuroY += Math.cos(headingRad) * velocity * deltaTime;

        	// Limita a posição futura para não sair do campo de batalha
        	futuroX = Math.max(Math.min(futuroX, getBattleFieldWidth() - 18), 18);
        	futuroY = Math.max(Math.min(futuroY, getBattleFieldHeight() - 18), 18);
    }
        // Calcula o ângulo para a posição futura
    	double anguloParaFuturo = Math.toDegrees(Math.atan2(futuroX - myX, futuroY - myY));
        
        // Retorna o quanto precisa girar o canhão
    	return normalRelativeAngleDegrees(anguloParaFuturo - getGunHeading());
    }

    // ===== EVENTO: QUANDO DETECTA UM INIMIGO =====
    public void onScannedRobot(ScannedRobotEvent e) {
        
        // Se já estamos rastreando outro robô, ignora este
        if (trackName != null && !e.getName().equals(trackName)) {
            return;
        }

        // Define o alvo
        if (trackName == null) {
            trackName = e.getName();
        }
        
        // Reseta o contador (alvo encontrado)
        count = 0;
		
		// ARMAZENA DADOS DO INIMIGO PARA PREDIÇÃO
        enemyVelocity = e.getVelocity();
        enemyHeading = e.getHeading();
        enemyDistance = e.getDistance();
        enemyBearing = e.getBearing();

    
        // Calcula o ângulo para o radar ficar travado no alvo
        double radarTurn = normalRelativeAngleDegrees(
            getHeading() + e.getBearing() - getRadarHeading()
        );
        
        // Adiciona um pequeno movimento extra para não perder o alvo
        // Se o radar está girando para a direita, adiciona +20, senão -20
        if (radarTurn > 0) {
            radarTurn += 20;
        } else {
            radarTurn -= 20;
        }
        setTurnRadarRight(radarTurn);

		// MIRA O CANHÃO COM PREDIÇÃO
        double gunTurnPreditivo = calcularAnguloPreditivo();
        setTurnGunRight(gunTurnPreditivo);


        // Ajusta a força do disparo dependendo da distância
        if (e.getDistance() > 200) {
            lastFirePower = 1.5;
        } else if (e.getDistance() > 100) {
            lastFirePower = 2;
        } else {
            lastFirePower = 3;
        }

        wantToFire = true;
        
    }

    // ===== EVENTO: QUANDO BATE NA PAREDE =====
    public void onHitWall(HitWallEvent e) {
        
        // Para imediatamente
        setAhead(0);
        
        // Vira 90 graus para o lado oposto da parede
        double bearing = e.getBearing();
        setTurnRight(90 - bearing);
        
        // Anda bastante para se afastar
        setAhead(200);
        
        // Reseta a espiral para começar pequena novamente
        passo = 10;
        expandindo = true;
        
        execute();
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
            turnRight(30);
			turnLeft(30);
        }
    }
}

