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
    int direcaoLateral = 1;

    // VARIÁVEIS PARA PREDIÇÃO
    double enemyVelocity = 0;        // velocidade do inimigo
    double enemyHeading = 0;         // direção do inimigo
    double enemyDistance = 0;        // distância do inimigo
    double enemyBearing = 0;         // ângulo relativo ao inimigo

    // VARIÁVEIS PARA CONTROLE DE MODO
    static final double DISTANCIA_ATIVACAO_ESPIRAL = 150.0;
    boolean modoEspiral = false;
    long ultimoScan = 0;

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
            // Verifica se perdeu o alvo
            if (trackName != null && getTime() - ultimoScan > 30) {
                trackName = null;
                modoEspiral = false;
            }

            // === MOVIMENTO BASEADO NO MODO ===
            if (trackName == null) {
                movimentoBusca();
            } else if (!modoEspiral) {
                movimentoPerseguicao();
            } else {
                movimentoEspiral();
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
            if (getTime() - ultimoTempoMudancaDirecao > 40 && Math.random() > 0.6) {
                direcaoMovimento *= -1;
                ultimoTempoMudancaDirecao = getTime();
            }
        }
    }

        // ===== MOVIMENTO DE BUSCA =====
    private void movimentoBusca() {
        setTurnRadarRight(360);
        setTurnRight(5);
        setAhead(50);
    }

    // ===== MOVIMENTO DE PERSEGUIÇÃO =====
    private void movimentoPerseguicao() {
        double margemSeguranca = 80;
        double x = getX();
        double y = getY();
        double largura = getBattleFieldWidth();
        double altura = getBattleFieldHeight();

        double anguloAbsolutoInimigo = getHeading() + enemyBearing;
        double enemyX = x + Math.sin(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        double enemyY = y + Math.cos(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;

        double anguloParaInimigo = Math.toDegrees(Math.atan2(enemyX - x, enemyY - y));
        double virar = normalRelativeAngleDegrees(anguloParaInimigo - getHeading());

        if (x < margemSeguranca || x > largura - margemSeguranca ||
            y < margemSeguranca || y > altura - margemSeguranca) {
            
            double anguloCentro = Math.toDegrees(Math.atan2(
                largura / 2 - x,
                altura / 2 - y
            ));
            double anguloCorrecao = normalRelativeAngleDegrees(anguloCentro - getHeading());
            setTurnRight(anguloCorrecao);
            setAhead(100);
        } else {
           double anguloPerp = normalRelativeAngleDegrees(anguloAbsolutoInimigo - getHeading() + 90 * direcaoLateral);
            
            if (enemyDistance > 300) {
                double anguloMisto = (virar + anguloPerp) / 2;
                setTurnRight(anguloMisto);
            } else {
                setTurnRight(anguloPerp);
            }
            setAhead(100);
            
            if (Math.random() > 0.9) {
                direcaoLateral *= -1;
            }
        }
    }

    // ===== MOVIMENTO EM ESPIRAL =====
    private void movimentoEspiral() {
        double margemSeguranca = 80;
        double x = getX();
        double y = getY();
        double largura = getBattleFieldWidth();
        double altura = getBattleFieldHeight();

        if (x < margemSeguranca || x > largura - margemSeguranca ||
            y < margemSeguranca || y > altura - margemSeguranca) {
            
            double anguloCentro = Math.toDegrees(Math.atan2(
                largura / 2 - x,
                altura / 2 - y
            ));
            double anguloCorrecao = normalRelativeAngleDegrees(anguloCentro - getHeading());
            setTurnRight(anguloCorrecao);
            setAhead(150);
        } else {
            double anguloMovimento = angTurn * direcaoMovimento;

            double proximoX = x + Math.sin(Math.toRadians(getHeading() + anguloMovimento)) * passo;
            double proximoY = y + Math.cos(Math.toRadians(getHeading() + anguloMovimento)) * passo;

            if (proximoX < 30 || proximoX > largura - 30 || proximoY < 30 || proximoY > altura - 30) {
                setTurnRight(90 * Math.signum(Math.random() - 0.5));
            } else {
                setTurnRight(anguloMovimento);
            }

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
        ultimoScan = getTime();
		
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

        if (enemyDistance <= DISTANCIA_ATIVACAO_ESPIRAL) {
            if (!modoEspiral) {
                modoEspiral = true;
                passo = 10;
                expandindo = true;
            }
        } else {
            modoEspiral = false;
        }
        
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
        direcaoLateral *= -1;
        
        execute();
    }

    // ===== EVENTO: QUANDO BATE EM OUTRO ROBÔ =====
    public void onHitRobot(HitRobotEvent e) {
        double bearing = e.getBearing();

    	// Se o inimigo estiver na frente, atira forte
	    if (Math.abs(bearing) < 30 && getGunHeat() == 0) {
    	    setFire(3);
	    }

    	// Sempre tenta se afastar para não travar
	    if (e.isMyFault()) {
    	    // Gira para longe e recua
        	setTurnRight(-bearing + (Math.random() > 0.5 ? 45 : -45));
	        setBack(150 + Math.random() * 100);
	        direcaoMovimento *= -1; // Inverte direção de movimento para ficar imprevisível
            direcaoLateral *= -1;
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