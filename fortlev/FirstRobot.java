package fortlev;

import robocode.*;
import static robocode.util.Utils.normalRelativeAngleDegrees;
import java.awt.*;
import robocode.HitWallEvent;
import robocode.HitRobotEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;


/**
 * AndersonSilva - a robot by (Arthur Abdala, Arthur de Oliveira, Mateus Raffaelli e Matheus Posada)
 * Versão com melhorias: Modo Kamikaze, Histórico de Posições, Predição com Aceleração, Anti-Canto
 */
public class FirstRobot extends AdvancedRobot {

    // ===== VARIÁVEIS DE CONTROLE =====
    double velocidadeRadar = 30;
    boolean girandoDireita = true;
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

    // VARIÁVEIS PARA PREDIÇÃO
    double enemyVelocity = 0;
    double enemyHeading = 0;
    double enemyDistance = 0;
    double enemyBearing = 0;
    double enemyEnergy = 100;
    
    // NEW: MELHORIA #4 - Histórico de posições do inimigo
    ArrayList<Point2D.Double> enemyHistory = new ArrayList<>();
    
    // NEW: MELHORIA #5 - Predição com aceleração
    double lastEnemyVelocity = 0;
    double enemyAcceleration = 0;
    
    // NEW: Detecção de padrões de movimento
    String enemyPattern = "UNKNOWN"; // STATIONARY, LINEAR, CIRCULAR, OSCILLATING
    double enemyAngularVelocity = 0; // velocidade angular (para circular)
    int scansParados = 0; // conta quantos scans o inimigo está parado

    // ===== MÉTODO PRINCIPAL =====
    public void run() {
        // === CONFIGURAÇÃO DE CORES ===
        setBodyColor(Color.black);
        setGunColor(Color.white);
        setRadarColor(Color.white);
        setScanColor(Color.red);
        setBulletColor(Color.yellow);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        trackName = null;
        gunTurnAmt = 10;

        // === LOOP PRINCIPAL ===
        while (true) {
            // NEW: MELHORIA #3 - Modo Kamikaze quando energia baixa
            boolean modoKamikaze = false;
            if (getEnergy() < 15 && trackName != null && enemyEnergy < 30 && enemyDistance < 400) {
                modoKamikaze = true;
                System.out.println("MODO KAMIKAZE ATIVADO!");
                
                // Vai direto para cima do inimigo
                double bearing = getHeading() + enemyBearing;
                double virarParaInimigo = normalRelativeAngleDegrees(bearing - getHeading());
                
                setTurnRight(virarParaInimigo);
                setAhead(enemyDistance);
                
                // Atira com força máxima
                if (getGunHeat() == 0) {
                    setFire(3);
                }
                
                execute();
                continue; // Pula resto da lógica
            }
            
            // === EVITAR PAREDES ===
            double margemSeguranca = 80;
            boolean pertoParede = false;
            
            double largura = getBattleFieldWidth();
            double altura = getBattleFieldHeight();
            double x = getX();
            double y = getY();
            
            // NEW: MELHORIA #6 - Detecta se está em um canto
            boolean emCanto = (x < 150 && y < 150) || 
                              (x < 150 && y > altura - 150) ||
                              (x > largura - 150 && y < 150) ||
                              (x > largura - 150 && y > altura - 150);
            
            if (emCanto) {
                System.out.println("ENCURRALADO NO CANTO! Escape diagonal");
                
                // Escape diagonal agressivo do canto
                double anguloParaCentro = Math.toDegrees(Math.atan2(
                    largura/2 - x,
                    altura/2 - y
                ));
                
                double virar = normalRelativeAngleDegrees(anguloParaCentro - getHeading());
                setTurnRight(virar);
                setAhead(200); // Movimento agressivo para sair
                
                execute();
                continue;
            }
            
            if (x < margemSeguranca || 
                x > largura - margemSeguranca ||
                y < margemSeguranca || 
                y > altura - margemSeguranca) {
                
                pertoParede = true;
                
                double anguloParaCentro = Math.toDegrees(Math.atan2(
                    largura/2 - x,
                    altura/2 - y
                ));
                
                double virar = normalRelativeAngleDegrees(anguloParaCentro - getHeading());
                
                setTurnRight(virar);
                setAhead(150);
            }

            // === MOVIMENTO EM ESPIRAL ===
            if (!pertoParede) {
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
                    passo = passo + incremento;
                    if (passo > 150) expandindo = false;
                } else {
                    passo = passo - incremento;
                    if (passo < 10) {
                        expandindo = true;
                        setTurnRight(45);
                        setAhead(100);
                    }
                }
            }

            // === CONTROLE DO RADAR ===
            if (trackName == null) {
                setTurnRadarRight(360);
                setTurnGunRight(gunTurnAmt);
            }

            // === ATIRAR ===
            double gunRemaining = Math.abs(getGunTurnRemaining());
            double GUN_ANGLE_TOLERANCE = 10.0;
            
            if (trackName != null && wantToFire && 
                gunRemaining <= GUN_ANGLE_TOLERANCE && 
                getGunHeat() == 0) {
                
                fire(lastFirePower);
                wantToFire = false;
            }

            count++;
            if (count > 2) gunTurnAmt = -10;
            if (count > 5) gunTurnAmt = 10;
            if (count > 11) trackName = null;

            execute();

            if (getTime() - ultimoTempoMudancaDirecao > 50 && Math.random() > 0.7) {
                direcaoMovimento *= -1;
                ultimoTempoMudancaDirecao = getTime();
            }
        }
    }

    // ===== MÉTODO DE DETECÇÃO DE PADRÕES =====
    /**
     * Analisa o histórico de posições e detecta o padrão de movimento do inimigo
     */
    public void detectarPadrao() {
        if (enemyHistory.size() < 5) {
            enemyPattern = "UNKNOWN";
            return;
        }
        
        // Pega últimas 5 posições
        int size = enemyHistory.size();
        Point2D.Double p1 = enemyHistory.get(size - 5);
        Point2D.Double p2 = enemyHistory.get(size - 4);
        Point2D.Double p3 = enemyHistory.get(size - 3);
        Point2D.Double p4 = enemyHistory.get(size - 2);
        Point2D.Double p5 = enemyHistory.get(size - 1);
        
        // Calcula distâncias entre posições consecutivas
        double dist1 = p1.distance(p2);
        double dist2 = p2.distance(p3);
        double dist3 = p3.distance(p4);
        double dist4 = p4.distance(p5);
        
        // === PADRÃO 1: PARADO ===
        if (dist1 < 5 && dist2 < 5 && dist3 < 5 && dist4 < 5) {
            enemyPattern = "STATIONARY";
            scansParados++;
            System.out.println("PADRÃO: Inimigo PARADO (" + scansParados + " scans)");
            return;
        } else {
            scansParados = 0;
        }
        
        // === PADRÃO 2: MOVIMENTO LINEAR ===
        // Calcula vetores de movimento
        double dx1 = p2.x - p1.x;
        double dy1 = p2.y - p1.y;
        double dx2 = p3.x - p2.x;
        double dy2 = p3.y - p2.y;
        double dx3 = p4.x - p3.x;
        double dy3 = p4.y - p3.y;
        double dx4 = p5.x - p4.x;
        double dy4 = p5.y - p4.y;
        
        // Normaliza vetores
        double len1 = Math.sqrt(dx1*dx1 + dy1*dy1);
        double len2 = Math.sqrt(dx2*dx2 + dy2*dy2);
        double len3 = Math.sqrt(dx3*dx3 + dy3*dy3);
        double len4 = Math.sqrt(dx4*dx4 + dy4*dy4);
        
        if (len1 > 0) { dx1 /= len1; dy1 /= len1; }
        if (len2 > 0) { dx2 /= len2; dy2 /= len2; }
        if (len3 > 0) { dx3 /= len3; dy3 /= len3; }
        if (len4 > 0) { dx4 /= len4; dy4 /= len4; }
        
        // Calcula similaridade dos vetores (produto escalar)
        double sim1 = dx1*dx2 + dy1*dy2;
        double sim2 = dx2*dx3 + dy2*dy3;
        double sim3 = dx3*dx4 + dy3*dy4;
        
        // Se vetores são similares (>0.9), movimento é linear
        if (sim1 > 0.9 && sim2 > 0.9 && sim3 > 0.9) {
            enemyPattern = "LINEAR";
            System.out.println("PADRÃO: Movimento LINEAR (linha reta)");
            return;
        }
        
        // === PADRÃO 3: MOVIMENTO CIRCULAR ===
        // Calcula centro aproximado do círculo
        double centerX = (p1.x + p2.x + p3.x + p4.x + p5.x) / 5.0;
        double centerY = (p1.y + p2.y + p3.y + p4.y + p5.y) / 5.0;
        
        // Calcula distâncias do centro
        double r1 = Math.sqrt(Math.pow(p1.x - centerX, 2) + Math.pow(p1.y - centerY, 2));
        double r2 = Math.sqrt(Math.pow(p2.x - centerX, 2) + Math.pow(p2.y - centerY, 2));
        double r3 = Math.sqrt(Math.pow(p3.x - centerX, 2) + Math.pow(p3.y - centerY, 2));
        double r4 = Math.sqrt(Math.pow(p4.x - centerX, 2) + Math.pow(p4.y - centerY, 2));
        double r5 = Math.sqrt(Math.pow(p5.x - centerX, 2) + Math.pow(p5.y - centerY, 2));
        
        // Se distâncias são similares (variação < 20%), é circular
        double avgRadius = (r1 + r2 + r3 + r4 + r5) / 5.0;
        double maxDiff = Math.max(Math.max(Math.abs(r1-avgRadius), Math.abs(r2-avgRadius)), 
                                  Math.max(Math.abs(r3-avgRadius), Math.abs(r4-avgRadius)));
        maxDiff = Math.max(maxDiff, Math.abs(r5-avgRadius));
        
        if (maxDiff < avgRadius * 0.2 && avgRadius > 50) {
            enemyPattern = "CIRCULAR";
            
            // Calcula velocidade angular
            double angle1 = Math.atan2(p4.y - centerY, p4.x - centerX);
            double angle2 = Math.atan2(p5.y - centerY, p5.x - centerX);
            enemyAngularVelocity = angle2 - angle1;
            
            System.out.println("PADRÃO: Movimento CIRCULAR (raio=" + (int)avgRadius + ")");
            return;
        }
        
        // === PADRÃO 4: OSCILANDO (zigue-zague) ===
        // Calcula mudanças de direção
        double angle1 = Math.atan2(dy1, dx1);
        double angle2 = Math.atan2(dy2, dx2);
        double angle3 = Math.atan2(dy3, dx3);
        double angle4 = Math.atan2(dy4, dx4);
        
        double turn1 = Math.abs(angle2 - angle1);
        double turn2 = Math.abs(angle3 - angle2);
        double turn3 = Math.abs(angle4 - angle3);
        
        // Se muda direção frequentemente (>45 graus), está oscilando
        if (turn1 > Math.PI/4 && turn2 > Math.PI/4 && turn3 > Math.PI/4) {
            enemyPattern = "OSCILLATING";
            System.out.println("PADRÃO: OSCILANDO (zigue-zague)");
            return;
        }
        
        // Padrão desconhecido/complexo
        enemyPattern = "COMPLEX";
    }
    
    // ===== MÉTODO DE PREDIÇÃO ADAPTATIVA =====
    // ===== MÉTODO DE PREDIÇÃO ADAPTATIVA =====
    /**
     * Predição que se adapta ao padrão de movimento detectado
     */
    public double calcularAnguloPreditivo() {
        double bulletSpeed = 20 - 3 * lastFirePower;
        double myX = getX();
        double myY = getY();
               
        double anguloAbsolutoInimigo = getHeading() + enemyBearing;
        double enemyX = myX + Math.sin(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        double enemyY = myY + Math.cos(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        
        double futuroX = enemyX;
        double futuroY = enemyY;
        
        // === PREDIÇÃO ADAPTATIVA BASEADA NO PADRÃO ===
        
        if (enemyPattern.equals("STATIONARY")) {
            // Inimigo parado - mira diretamente nele
            futuroX = enemyX;
            futuroY = enemyY;
            
        } else if (enemyPattern.equals("LINEAR")) {
            // Movimento linear - usa predição simples
            double headingRad = Math.toRadians(enemyHeading);
            double velocity = enemyVelocity;
            double deltaTime = 1;
            
            for (double t = 0; t * bulletSpeed < Point2D.distance(myX, myY, futuroX, futuroY); t += deltaTime) {
                double velocityAtual = velocity + (enemyAcceleration * t);
                
                futuroX += Math.sin(headingRad) * velocityAtual * deltaTime;
                futuroY += Math.cos(headingRad) * velocityAtual * deltaTime;

                futuroX = Math.max(Math.min(futuroX, getBattleFieldWidth() - 18), 18);
                futuroY = Math.max(Math.min(futuroY, getBattleFieldHeight() - 18), 18);
            }
            
        } else if (enemyPattern.equals("CIRCULAR")) {
            // Movimento circular - prediz baseado em velocidade angular
            int size = enemyHistory.size();
            if (size >= 5) {
                Point2D.Double p3 = enemyHistory.get(size - 3);
                Point2D.Double p4 = enemyHistory.get(size - 2);
                Point2D.Double p5 = enemyHistory.get(size - 1);
                
                // Calcula centro do círculo
                double centerX = (p3.x + p4.x + p5.x) / 3.0;
                double centerY = (p3.y + p4.y + p5.y) / 3.0;
                
                // Tempo até bala chegar
                double timeToHit = Point2D.distance(myX, myY, enemyX, enemyY) / bulletSpeed;
                
                // Prediz posição futura baseado na rotação
                double currentAngle = Math.atan2(enemyY - centerY, enemyX - centerX);
                double futureAngle = currentAngle + (enemyAngularVelocity * timeToHit);
                double radius = Point2D.distance(enemyX, enemyY, centerX, centerY);
                
                futuroX = centerX + Math.cos(futureAngle) * radius;
                futuroY = centerY + Math.sin(futureAngle) * radius;
                
                // Limita ao campo
                futuroX = Math.max(Math.min(futuroX, getBattleFieldWidth() - 18), 18);
                futuroY = Math.max(Math.min(futuroY, getBattleFieldHeight() - 18), 18);
            }
            
        } else if (enemyPattern.equals("OSCILLATING")) {
            // Oscilando - atira onde ele ESTÁ (difícil prever zigue-zague)
            // Mas adiciona pequena compensação baseada na velocidade média
            if (enemyHistory.size() >= 3) {
                Point2D.Double p3 = enemyHistory.get(enemyHistory.size() - 3);
                Point2D.Double p5 = enemyHistory.get(enemyHistory.size() - 1);
                
                double avgDx = (p5.x - p3.x) / 2.0;
                double avgDy = (p5.y - p3.y) / 2.0;
                
                double timeToHit = enemyDistance / bulletSpeed;
                futuroX = enemyX + avgDx * timeToHit;
                futuroY = enemyY + avgDy * timeToHit;
            }
            
        } else {
            // UNKNOWN/COMPLEX - usa predição padrão com aceleração
            double headingRad = Math.toRadians(enemyHeading);
            double velocity = enemyVelocity;
            double deltaTime = 1;
            
            for (double t = 0; t * bulletSpeed < Point2D.distance(myX, myY, futuroX, futuroY); t += deltaTime) {
                double velocityAtual = velocity + (enemyAcceleration * t);
                
                futuroX += Math.sin(headingRad) * velocityAtual * deltaTime;
                futuroY += Math.cos(headingRad) * velocityAtual * deltaTime;

                futuroX = Math.max(Math.min(futuroX, getBattleFieldWidth() - 18), 18);
                futuroY = Math.max(Math.min(futuroY, getBattleFieldHeight() - 18), 18);
            }
        }
        
        // Calcula ângulo para posição futura
        double anguloParaFuturo = Math.toDegrees(Math.atan2(futuroX - myX, futuroY - myY));
        
        return normalRelativeAngleDegrees(anguloParaFuturo - getGunHeading());
    }

    // ===== EVENTO: QUANDO DETECTA INIMIGO =====
    public void onScannedRobot(ScannedRobotEvent e) {
        
        if (trackName != null && !e.getName().equals(trackName)) {
            return;
        }

        if (trackName == null) {
            trackName = e.getName();
        }
        
        count = 0;
        
        // ARMAZENA DADOS DO INIMIGO
        enemyVelocity = e.getVelocity();
        enemyHeading = e.getHeading();
        enemyDistance = e.getDistance();
        enemyBearing = e.getBearing();
        enemyEnergy = e.getEnergy(); // NEW: rastreia energia
        
        // NEW: MELHORIA #5 - Calcula aceleração
        enemyAcceleration = enemyVelocity - lastEnemyVelocity;
        lastEnemyVelocity = enemyVelocity;
        
        // NEW: MELHORIA #4 - Armazena histórico de posições
        double anguloAbs = getHeading() + e.getBearing();
        double ex = getX() + Math.sin(Math.toRadians(anguloAbs)) * e.getDistance();
        double ey = getY() + Math.cos(Math.toRadians(anguloAbs)) * e.getDistance();
        
        enemyHistory.add(new Point2D.Double(ex, ey));
        
        // Mantém apenas últimas 10 posições
        if (enemyHistory.size() > 10) {
            enemyHistory.remove(0);
        }
        
        // NEW: Detecta padrão de movimento
        detectarPadrao();
        
        // Ajusta estratégia baseado no padrão
        if (enemyPattern.equals("STATIONARY") && scansParados > 3) {
            // Inimigo parado há tempo - tiro mais forte
            lastFirePower = 3.0;
            System.out.println("ALVO FÁCIL: Inimigo parado!");
        } else if (enemyPattern.equals("CIRCULAR")) {
            // Movimento circular é previsível - aumenta potência
            lastFirePower = 2.5;
        } else if (enemyPattern.equals("OSCILLATING")) {
            // Zigue-zague é difícil - tiro mais fraco e rápido
            lastFirePower = 1.5;
        }
    
        // Radar lock
        double radarTurn = normalRelativeAngleDegrees(
            getHeading() + e.getBearing() - getRadarHeading()
        );
        
        if (radarTurn > 0) {
            radarTurn += 20;
        } else {
            radarTurn -= 20;
        }
        setTurnRadarRight(radarTurn);

        // Mira com predição melhorada
        double gunTurnPreditivo = calcularAnguloPreditivo();
        setTurnGunRight(gunTurnPreditivo);

        // Ajusta potência baseado na distância (sobrescreve ajuste de padrão se necessário)
        if (e.getDistance() > 200) {
            if (!enemyPattern.equals("STATIONARY")) {
                lastFirePower = 1.5;
            }
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
    	}
    }


    // ===== EVENTO: QUANDO VENCE =====
    public void onWin(WinEvent e) {
        for (int i = 0; i < 20; i++) {
            turnRight(30);
            turnLeft(30);
        }
    }
}