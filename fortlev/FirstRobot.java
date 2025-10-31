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
 * Versão melhorada com predição avançada
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
    
    // CONFIGURAÇÃO DE DISTÂNCIA PARA TRAVAR RADAR
    static final double DISTANCIA_TRAVAR_RADAR = 300; // Só trava se inimigo estiver a menos de 300 pixels
    static final double DISTANCIA_COMBATE = 250; // Distância ideal para começar movimentação em espiral

    // VARIÁVEIS PARA PREDIÇÃO BÁSICA
    double enemyVelocity = 0;
    double enemyHeading = 0;
    double enemyDistance = 0;
    double enemyBearing = 0;
    
    // ===== NOVAS VARIÁVEIS PARA PREDIÇÃO AVANÇADA =====
    ArrayList<EnemyState> historicoInimigo = new ArrayList<>();
    static final int MAX_HISTORICO = 20; // últimos 20 scans
    double enemyVelocityAnterior = 0;
    double enemyHeadingAnterior = 0;
    long ultimoScanTime = 0;
    
    // Estatísticas para padrões circulares
    double somaGirosPorTick = 0;
    int contadorScans = 0;
    
    // ===== CLASSE INTERNA PARA ARMAZENAR ESTADO DO INIMIGO =====
    class EnemyState {
        double x, y;
        double velocity;
        double heading;
        long time;
        
        EnemyState(double x, double y, double velocity, double heading, long time) {
            this.x = x;
            this.y = y;
            this.velocity = velocity;
            this.heading = heading;
            this.time = time;
        }
    }

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
            double x = getX();
            double y = getY();
            double largura = getBattleFieldWidth();
            double altura = getBattleFieldHeight();
            double margemSeguranca = 80;
            
            // ===== DECISÃO DE MOVIMENTO BASEADA NA DISTÂNCIA DO INIMIGO =====
            
            // CASO 1: Inimigo detectado e LONGE - Aproximar
            if (trackName != null && enemyDistance > DISTANCIA_COMBATE) {
                // Calcula ângulo para ir em direção ao inimigo
                double anguloParaInimigo = normalRelativeAngleDegrees(
                    getHeading() + enemyBearing - getHeading()
                );
                
                // Verifica se não vai colidir com parede ao se aproximar
                double proximoX = x + Math.sin(Math.toRadians(getHeading() + anguloParaInimigo)) * 100;
                double proximoY = y + Math.cos(Math.toRadians(getHeading() + anguloParaInimigo)) * 100;
                
                if (proximoX < margemSeguranca || proximoX > largura - margemSeguranca ||
                    proximoY < margemSeguranca || proximoY > altura - margemSeguranca) {
                    // Se vai bater na parede, contorna
                    double anguloCentro = Math.toDegrees(Math.atan2(
                        largura / 2 - x,
                        altura / 2 - y
                    ));
                    double anguloCorrecao = normalRelativeAngleDegrees(anguloCentro - getHeading());
                    setTurnRight(anguloCorrecao);
                    setAhead(100);
                } else {
                    // Caminho livre: vai direto para o inimigo
                    setTurnRight(anguloParaInimigo);
                    setAhead(100);
                }
            }
            // CASO 2: Inimigo PRÓXIMO ou SEM INIMIGO - Movimentação em espiral
            else {
                // === EVITAR PAREDES ===
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
                    // Movimento em espiral normal
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

            // === CONTROLE DO RADAR ===
            // Se NÃO temos alvo OU o alvo está longe, varre em círculos
            if (trackName == null || enemyDistance > DISTANCIA_TRAVAR_RADAR) {
                setTurnRadarRight(360);
                setTurnGunRight(gunTurnAmt);
            }
            // Se temos alvo E está próximo, o radar será travado no onScannedRobot

            // ATIRAR COM TOLERÂNCIA AJUSTADA
            double gunRemaining = Math.abs(getGunTurnRemaining());
            double GUN_ANGLE_TOLERANCE = 8.0;
            
            if (trackName != null && wantToFire && 
                gunRemaining <= GUN_ANGLE_TOLERANCE && 
                getGunHeat() == 0) {
                fire(lastFirePower);
                wantToFire = false;
            }

            count++;
            if (count > 2) gunTurnAmt = -10;
            if (count > 5) gunTurnAmt = 10;
            if (count > 11) {
                trackName = null;
                historicoInimigo.clear(); // Limpa histórico ao perder alvo
                contadorScans = 0;
                somaGirosPorTick = 0;
            }

            execute();

            if (getTime() - ultimoTempoMudancaDirecao > 40 && Math.random() > 0.6) {
                direcaoMovimento *= -1;
                ultimoTempoMudancaDirecao = getTime();
            }
        }
    }

    // ===== MÉTODO DE PREDIÇÃO AVANÇADA =====
    /**
     * Calcula o ângulo preditivo usando múltiplas estratégias:
     * 1. Detecção de movimento circular
     * 2. Predição com aceleração angular
     * 3. Fallback para predição linear
     */
    public double calcularAnguloPreditivo() {
        double bulletSpeed = 20 - 3 * lastFirePower;
        double myX = getX();
        double myY = getY();
        
        // Posição atual do inimigo
        double anguloAbsolutoInimigo = getHeading() + enemyBearing;
        double enemyX = myX + Math.sin(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        double enemyY = myY + Math.cos(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        
        // Se não temos histórico suficiente, usa predição linear simples
        if (historicoInimigo.size() < 3) {
            return predicaoLinearSimples(myX, myY, enemyX, enemyY, bulletSpeed);
        }
        
        // Calcula taxa de giro média (para detectar movimento circular)
        double giroMedioPorTick = somaGirosPorTick / contadorScans;
        
        // Se o inimigo está girando consistentemente, usa predição circular
        if (Math.abs(giroMedioPorTick) > 1.5) { // Threshold de 1.5 graus/tick
            return predicaoCircular(myX, myY, enemyX, enemyY, bulletSpeed, giroMedioPorTick);
        }
        
        // Caso contrário, usa predição com aceleração angular
        return predicaoComAceleracao(myX, myY, enemyX, enemyY, bulletSpeed);
    }
    
    // ===== PREDIÇÃO LINEAR SIMPLES =====
    private double predicaoLinearSimples(double myX, double myY, double enemyX, double enemyY, double bulletSpeed) {
        double headingRad = Math.toRadians(enemyHeading);
        double velocity = enemyVelocity;
        
        double futuroX = enemyX;
        double futuroY = enemyY;
        
        double deltaTime = 1;
        for (double t = 0; t * bulletSpeed < Point2D.distance(myX, myY, futuroX, futuroY); t += deltaTime) {
            futuroX += Math.sin(headingRad) * velocity * deltaTime;
            futuroY += Math.cos(headingRad) * velocity * deltaTime;
            
            futuroX = Math.max(Math.min(futuroX, getBattleFieldWidth() - 18), 18);
            futuroY = Math.max(Math.min(futuroY, getBattleFieldHeight() - 18), 18);
        }
        
        double anguloParaFuturo = Math.toDegrees(Math.atan2(futuroX - myX, futuroY - myY));
        return normalRelativeAngleDegrees(anguloParaFuturo - getGunHeading());
    }
    
    // ===== PREDIÇÃO CIRCULAR (para inimigos em movimento circular) =====
    private double predicaoCircular(double myX, double myY, double enemyX, double enemyY, 
                                    double bulletSpeed, double giroMedioPorTick) {
        double headingRad = Math.toRadians(enemyHeading);
        double velocity = enemyVelocity;
        double giroRadPorTick = Math.toRadians(giroMedioPorTick);
        
        double futuroX = enemyX;
        double futuroY = enemyY;
        double futuroHeading = headingRad;
        
        double deltaTime = 1;
        for (double t = 0; t * bulletSpeed < Point2D.distance(myX, myY, futuroX, futuroY); t += deltaTime) {
            // Atualiza direção considerando o giro
            futuroHeading += giroRadPorTick * deltaTime;
            
            // Atualiza posição
            futuroX += Math.sin(futuroHeading) * velocity * deltaTime;
            futuroY += Math.cos(futuroHeading) * velocity * deltaTime;
            
            // Limita ao campo
            futuroX = Math.max(Math.min(futuroX, getBattleFieldWidth() - 18), 18);
            futuroY = Math.max(Math.min(futuroY, getBattleFieldHeight() - 18), 18);
        }
        
        double anguloParaFuturo = Math.toDegrees(Math.atan2(futuroX - myX, futuroY - myY));
        return normalRelativeAngleDegrees(anguloParaFuturo - getGunHeading());
    }
    
    // ===== PREDIÇÃO COM ACELERAÇÃO ANGULAR =====
    private double predicaoComAceleracao(double myX, double myY, double enemyX, double enemyY, double bulletSpeed) {
        if (historicoInimigo.size() < 2) {
            return predicaoLinearSimples(myX, myY, enemyX, enemyY, bulletSpeed);
        }
        
        // Pega os dois últimos estados
        EnemyState atual = historicoInimigo.get(historicoInimigo.size() - 1);
        EnemyState anterior = historicoInimigo.get(historicoInimigo.size() - 2);
        
        // Calcula aceleração angular
        double giroAtual = normalRelativeAngleDegrees(atual.heading - anterior.heading);
        double aceleracaoAngular = 0;
        
        if (historicoInimigo.size() >= 3) {
            EnemyState maisAntigo = historicoInimigo.get(historicoInimigo.size() - 3);
            double giroAnterior = normalRelativeAngleDegrees(anterior.heading - maisAntigo.heading);
            aceleracaoAngular = giroAtual - giroAnterior;
        }
        
        double headingRad = Math.toRadians(atual.heading);
        double velocity = atual.velocity;
        double giroRad = Math.toRadians(giroAtual);
        double aceleracaoRad = Math.toRadians(aceleracaoAngular);
        
        double futuroX = enemyX;
        double futuroY = enemyY;
        double futuroHeading = headingRad;
        double futuroGiro = giroRad;
        
        double deltaTime = 1;
        for (double t = 0; t * bulletSpeed < Point2D.distance(myX, myY, futuroX, futuroY); t += deltaTime) {
            // Atualiza giro com aceleração
            futuroGiro += aceleracaoRad * deltaTime;
            futuroHeading += futuroGiro * deltaTime;
            
            // Atualiza posição
            futuroX += Math.sin(futuroHeading) * velocity * deltaTime;
            futuroY += Math.cos(futuroHeading) * velocity * deltaTime;
            
            // Limita ao campo
            futuroX = Math.max(Math.min(futuroX, getBattleFieldWidth() - 18), 18);
            futuroY = Math.max(Math.min(futuroY, getBattleFieldHeight() - 18), 18);
        }
        
        double anguloParaFuturo = Math.toDegrees(Math.atan2(futuroX - myX, futuroY - myY));
        return normalRelativeAngleDegrees(anguloParaFuturo - getGunHeading());
    }

    // ===== EVENTO: QUANDO DETECTA UM INIMIGO =====
    public void onScannedRobot(ScannedRobotEvent e) {
        if (trackName != null && !e.getName().equals(trackName)) {
            return;
        }

        if (trackName == null) {
            trackName = e.getName();
        }
        
        count = 0;
        
        // Armazena dados básicos
        enemyVelocity = e.getVelocity();
        enemyHeading = e.getHeading();
        enemyDistance = e.getDistance();
        enemyBearing = e.getBearing();
        
        // ===== CALCULA POSIÇÃO ABSOLUTA DO INIMIGO =====
        double anguloAbsolutoInimigo = getHeading() + e.getBearing();
        double enemyX = getX() + Math.sin(Math.toRadians(anguloAbsolutoInimigo)) * e.getDistance();
        double enemyY = getY() + Math.cos(Math.toRadians(anguloAbsolutoInimigo)) * e.getDistance();
        
        // ===== ADICIONA AO HISTÓRICO =====
        historicoInimigo.add(new EnemyState(enemyX, enemyY, e.getVelocity(), e.getHeading(), getTime()));
        
        // Mantém tamanho do histórico limitado
        if (historicoInimigo.size() > MAX_HISTORICO) {
            historicoInimigo.remove(0);
        }
        
        // ===== CALCULA TAXA DE GIRO =====
        if (ultimoScanTime > 0 && contadorScans > 0) {
            double giroAtual = normalRelativeAngleDegrees(e.getHeading() - enemyHeadingAnterior);
            somaGirosPorTick += giroAtual;
            contadorScans++;
        } else {
            contadorScans = 1;
            somaGirosPorTick = 0;
        }
        
        enemyVelocityAnterior = enemyVelocity;
        enemyHeadingAnterior = enemyHeading;
        ultimoScanTime = getTime();
    
        // ===== CONTROLE DO RADAR - SÓ TRAVA SE ESTIVER PRÓXIMO =====
        if (e.getDistance() <= DISTANCIA_TRAVAR_RADAR) {
            // Inimigo próximo: TRAVA o radar nele
            double radarTurn = normalRelativeAngleDegrees(
                getHeading() + e.getBearing() - getRadarHeading()
            );
            
            if (radarTurn > 0) {
                radarTurn += 20;
            } else {
                radarTurn -= 20;
            }
            setTurnRadarRight(radarTurn);
        }
        // Se estiver longe, o radar continua varrendo (configurado no loop principal)

        // MIRA COM PREDIÇÃO AVANÇADA
        double gunTurnPreditivo = calcularAnguloPreditivo();
        setTurnGunRight(gunTurnPreditivo);

        // Ajusta força do disparo
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
        setAhead(0);
        double bearing = e.getBearing();
        setTurnRight(90 - bearing);
        setAhead(200);
        passo = 10;
        expandindo = true;
        execute();
    }

    // ===== EVENTO: QUANDO BATE EM OUTRO ROBÔ =====
    public void onHitRobot(HitRobotEvent e) {
        double bearing = e.getBearing();

        if (Math.abs(bearing) < 30 && getGunHeat() == 0) {
            setFire(3);
        }

        if (e.isMyFault()) {
            setTurnRight(-bearing + (Math.random() > 0.5 ? 45 : -45));
            setBack(150 + Math.random() * 100);
            direcaoMovimento *= -1;
        }
    }

    // ===== EVENTO: QUANDO VENCE A PARTIDA =====
    public void onWin(WinEvent e) {
        for (int i = 0; i < 20; i++) {
            turnRight(30);
            turnLeft(30);
        }
    }
}