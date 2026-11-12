package fortlev;

import robocode.*;
import static robocode.util.Utils.normalRelativeAngleDegrees;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * Whatsapp2premium - a robot by (Arthur Abdala, Arthur de Oliveira, Mateus Raffaelli e Matheus Posada)
 * Robô híbrido que usa estratégia para múltiplos inimigos quando há mais de 1 inimigo,
 * e estratégia 1x1 quando há apenas 1 inimigo.
 */
public class Whatsapp2premium extends AdvancedRobot {

    // ===== VARIÁVEIS DE CONTROLE =====
    private boolean modo1x1 = false;
    
    // Variáveis para modo múltiplos inimigos
    private double passo = 1.0;            // distância que o robô anda a cada iteração (controla o raio da espiral)
    private double incremento = 0.08;      // quanto o "passo" aumenta ou diminui (abre/fecha a espiral)
    private double angTurn = 3.0;          // ângulo que o corpo gira por ciclo
    private boolean expandindo = true;     // indica se a espiral está abrindo (true) ou fechando (false)
    private int count = 0;
    private double gunTurnAmt;
    private String trackName;
    private boolean wantToFire = false;
    private double lastFirePower = 3;
    private int direcaoMovimento = 1;  // 1 = frente, -1 = trás (para esquiva)
    private long ultimoTempoMudancaDirecao = 0;
    
    // CONFIGURAÇÃO DE DISTÂNCIA PARA TRAVAR RADAR
    private static final double DISTANCIA_TRAVAR_RADAR = 250;
    private static final double DISTANCIA_COMBATE = 150;

    // VARIÁVEIS PARA PREDIÇÃO
    private double enemyVelocity = 0;        // velocidade do inimigo
    private double enemyHeading = 0;         // direção do inimigo
    private double enemyDistance = 0;        // distância do inimigo
    private double enemyBearing = 0;         // ângulo relativo ao inimigo
    
    private ArrayList<EnemyState> historicoInimigo = new ArrayList<>();
    private static final int MAX_HISTORICO = 20;
    private double enemyVelocityAnterior = 0;
    private double enemyHeadingAnterior = 0;
    private long ultimoScanTime = 0;
    
    private double somaGirosPorTick = 0;
    private int contadorScans = 0;
    
    private int missedShots = 0;
    private static final int MAX_MISSED_SHOTS = 5;
    private long lastHitTime = 0;
    private boolean searchingNewTarget = false;
    
    // Variáveis para modo 1x1
    private int moveDirection = 1;

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
        // Decide o modo baseado no número de inimigos
        modo1x1 = (getOthers() == 1);
        
        if (modo1x1) {
            // Configuração para modo 1x1
            setAdjustRadarForRobotTurn(true);
            setBodyColor(new Color(244, 0, 161));
            setGunColor(new Color(244, 0, 161));
            setRadarColor(new Color(244, 0, 161));
            setScanColor(Color.white);
            setBulletColor(Color.white);
            setAdjustGunForRobotTurn(true);
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        } else {
            // Configuração para modo múltiplos inimigos
            setBodyColor(Color.black);
            setGunColor(Color.white);
            setRadarColor(Color.white);
            setScanColor(Color.red);
            setBulletColor(Color.yellow);
            setAdjustGunForRobotTurn(true);
            setAdjustRadarForGunTurn(true);
            trackName = null;
            gunTurnAmt = 10;
        }

        // === LOOP PRINCIPAL (executa até o fim da partida) ===
        while (true) {
            if (modo1x1) {
                // Modo 1x1 - movimento simples
                setAhead(100 * moveDirection);
                execute();
            } else {
                // Modo múltiplos inimigos
                double x = getX();
                double y = getY();
                double largura = getBattleFieldWidth();
                double altura = getBattleFieldHeight();
                double margemSeguranca = 80;
                
                // Troca de alvo se muitos tiros errados
                if (missedShots >= MAX_MISSED_SHOTS && trackName != null) {
                    trocarAlvo();
                }
                
                // === EVITAR PAREDES (USANDO "WALL SMOOTHING") ===
                if (trackName != null && enemyDistance > DISTANCIA_COMBATE) {
                    // Se longe do inimigo, move em direção a ele
                    double anguloParaInimigo = normalRelativeAngleDegrees(
                        getHeading() + enemyBearing - getHeading()
                    );
                    
                    double proximoX = x + Math.sin(Math.toRadians(getHeading() + anguloParaInimigo)) * 100;
                    double proximoY = y + Math.cos(Math.toRadians(getHeading() + anguloParaInimigo)) * 100;
                    
                    // Verifica proximidade das paredes
                    if (proximoX < margemSeguranca || proximoX > largura - margemSeguranca ||
                        proximoY < margemSeguranca || proximoY > altura - margemSeguranca) {
                        // Calcula ângulo para o centro do campo
                        double anguloCentro = Math.toDegrees(Math.atan2(
                            largura / 2 - x,
                            altura / 2 - y
                        ));
                        double anguloCorrecao = normalRelativeAngleDegrees(anguloCentro - getHeading());
                        setTurnRight(anguloCorrecao);
                        setAhead(100);
                    } else {
                        setTurnRight(anguloParaInimigo);
                        setAhead(100);
                    }
                } else {
                    // Verifica proximidade das paredes
                    if (x < margemSeguranca || x > largura - margemSeguranca ||
                        y < margemSeguranca || y > altura - margemSeguranca) {
                        
                        // Calcula ângulo para o centro do campo
                        double anguloCentro = Math.toDegrees(Math.atan2(
                            largura / 2 - x,
                            altura / 2 - y
                        ));
                        double anguloCorrecao = normalRelativeAngleDegrees(anguloCentro - getHeading());
                        setTurnRight(anguloCorrecao);
                        setAhead(150);
                    } else {
                        // Movimento normal (espiral com suavização)
                        double anguloMovimento = angTurn * direcaoMovimento;
                        
                        // --- Ajuste de direção para não apontar para fora do mapa ---
                        double proximoX = x + Math.sin(Math.toRadians(getHeading() + anguloMovimento)) * passo;
                        double proximoY = y + Math.cos(Math.toRadians(getHeading() + anguloMovimento)) * passo;

                        if (proximoX < 30 || proximoX > largura - 30 || proximoY < 30 || proximoY > altura - 30) {
                            // Gira levemente para longe da parede
                            setTurnRight(90 * Math.signum(Math.random() - 0.5));
                        } else {
                            setTurnRight(anguloMovimento);
                        }

                        setAhead(passo * direcaoMovimento);

                        // Alterna entre expandir e contrair a espiral
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
                // Se NÃO temos alvo ou estamos longe, varre em círculos
                if (trackName == null || enemyDistance > DISTANCIA_TRAVAR_RADAR || searchingNewTarget) {
                    setTurnRadarRight(360);
                    setTurnGunRight(gunTurnAmt);
                }

                // ATIRAR COM CONDIÇÕES MAIS FLEXÍVEIS
                double gunRemaining = Math.abs(getGunTurnRemaining());
                double GUN_ANGLE_TOLERANCE = 8.0;  // Tolerância de 8 graus
                
                if (trackName != null && wantToFire && 
                    gunRemaining <= GUN_ANGLE_TOLERANCE && 
                    getGunHeat() == 0) {  // Verifica se canhão está frio
                    fire(lastFirePower);
                    wantToFire = false;
                }

                // Lógica de "procura" de alvo usando count
                count++;
                if (count > 2) gunTurnAmt = -10;
                if (count > 5) gunTurnAmt = 10;
                if (count > 11) {
                    trackName = null;
                    historicoInimigo.clear();
                    contadorScans = 0;
                    somaGirosPorTick = 0;
                }

                // Muda direção periodicamente para esquiva imprevisível
                if (getTime() - ultimoTempoMudancaDirecao > 40 && Math.random() > 0.6) {
                    direcaoMovimento *= -1;
                    ultimoTempoMudancaDirecao = getTime();
                }
                
                execute(); // Executa todos os comandos pendentes
            }
        }
    }

    // ===== EVENTO: QUANDO DETECTA UM INIMIGO =====
    public void onScannedRobot(ScannedRobotEvent e) {
        if (modo1x1) {
            // ===== ESTRATÉGIA 1x1 =====
            double absBearing = e.getBearingRadians() + getHeadingRadians();
            double latVel = e.getVelocity() * Math.sin(e.getHeadingRadians() - absBearing);
            double gunTurnAmt;
            double intensidade = 3; 
            
            setTurnRadarLeftRadians(getRadarTurnRemainingRadians());
            
            // Ajusta força do tiro baseado na distância
            if (e.getDistance() > 900){
                intensidade = 1;
            } else if (e.getDistance() > 450) {
                intensidade = 2;
            } else {
                intensidade = 3; 
            } 
            
            // Muda velocidade ocasionalmente para ser imprevisível
            if(Math.random() > .9){
                setMaxVelocity((12 * Math.random()) + 12);
            }
            
            // Estratégia de combate baseada na distância
            if (e.getDistance() > 150) {
                gunTurnAmt = robocode.util.Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + latVel / 22);
                setTurnGunRightRadians(gunTurnAmt);
                setTurnRightRadians(robocode.util.Utils.normalRelativeAngle(absBearing - getHeadingRadians() + latVel / getVelocity()));
                setAhead((e.getDistance() - 140) * moveDirection);
                setFire(3);
            } else {
                gunTurnAmt = robocode.util.Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + latVel / 15);
                setTurnGunRightRadians(gunTurnAmt);
                setTurnLeft(-90 - e.getBearing());
                setAhead((e.getDistance() - 140) * moveDirection);
                setFire(intensidade);
            }
        } else {
            // ===== ESTRATÉGIA MÚLTIPLOS INIMIGOS =====
            
            // Se estamos procurando novo alvo, define este
            if (searchingNewTarget) {
                trackName = e.getName();
                searchingNewTarget = false;
                missedShots = 0;
                out.println("NOVO ALVO ENCONTRADO: " + trackName);
            }
            
            // Se já estamos rastreando outro robô, ignora este
            if (trackName != null && !e.getName().equals(trackName)) {
                return;
            }

            // Define o alvo
            if (trackName == null) {
                trackName = e.getName();
                missedShots = 0;
            }
            
            // Reseta o contador (alvo encontrado)
            count = 0;
            
            // ARMAZENA DADOS DO INIMIGO PARA PREDIÇÃO
            enemyVelocity = e.getVelocity();
            enemyHeading = e.getHeading();
            enemyDistance = e.getDistance();
            enemyBearing = e.getBearing();
            
            // Calcula posição absoluta do inimigo
            double anguloAbsolutoInimigo = getHeading() + e.getBearing();
            double enemyX = getX() + Math.sin(Math.toRadians(anguloAbsolutoInimigo)) * e.getDistance();
            double enemyY = getY() + Math.cos(Math.toRadians(anguloAbsolutoInimigo)) * e.getDistance();
            
            // Adiciona ao histórico para predição
            historicoInimigo.add(new EnemyState(enemyX, enemyY, e.getVelocity(), e.getHeading(), getTime()));
            
            // Mantém histórico limitado
            if (historicoInimigo.size() > MAX_HISTORICO) {
                historicoInimigo.remove(0);
            }
            
            // Calcula giro médio do inimigo
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
        
            // Trava o radar no alvo quando perto
            if (e.getDistance() <= DISTANCIA_TRAVAR_RADAR) {
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
            }

            // MIRA O CANHÃO COM PREDIÇÃO AVANÇADA
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
    }

    // ===== MÉTODOS AUXILIARES PARA MODO MÚLTIPLOS INIMIGOS =====
    
    /**
     * Troca de alvo quando muitos tiros consecutivos erram
     */
    private void trocarAlvo() {
        out.println("MUITOS ERROS! Trocando de alvo... Erros consecutivos: " + missedShots);
        trackName = null;
        searchingNewTarget = true;
        missedShots = 0;
        historicoInimigo.clear();
        contadorScans = 0;
        somaGirosPorTick = 0;
        setTurnRadarRight(720);
        setTurnRight(90);
        setAhead(150);
        out.println("Procurando novo alvo...");
    }

    // ===== MÉTODO DE PREDIÇÃO DE TIRO =====
    /**
     * Calcula o ângulo para atirar prevendo onde o inimigo estará
     * Usa diferentes métodos de predição baseado no histórico
     */
    private double calcularAnguloPreditivo() {
        if (historicoInimigo.size() < 3) {
            return predicaoLinearSimples();
        }
        
        double giroMedioPorTick = somaGirosPorTick / contadorScans;
        
        // Se o inimigo está girando muito, usa predição circular
        if (Math.abs(giroMedioPorTick) > 1.5) {
            return predicaoCircular(giroMedioPorTick);
        }
        
        // Caso contrário, usa predição com aceleração
        return predicaoComAceleracao();
    }
    
    /**
     * Predição linear simples - assume movimento em linha reta
     */
    private double predicaoLinearSimples() {
        // Velocidade da bala baseada na potência do tiro
        double bulletSpeed = 20 - 3 * lastFirePower;
        // Posição atual do robô
        double myX = getX();
        double myY = getY();
        
        // Posição atual do inimigo em coordenadas absolutas
        double anguloAbsolutoInimigo = getHeading() + enemyBearing;
        double enemyX = myX + Math.sin(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        double enemyY = myY + Math.cos(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        
        // Velocidade e direção do adversário
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
    
    /**
     * Predição circular - para inimigos que estão girando
     */
    private double predicaoCircular(double giroMedioPorTick) {
        double bulletSpeed = 20 - 3 * lastFirePower;
        double myX = getX();
        double myY = getY();
        
        double anguloAbsolutoInimigo = getHeading() + enemyBearing;
        double enemyX = myX + Math.sin(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        double enemyY = myY + Math.cos(Math.toRadians(anguloAbsolutoInimigo)) * enemyDistance;
        
        double headingRad = Math.toRadians(enemyHeading);
        double velocity = enemyVelocity;
        double giroRadPorTick = Math.toRadians(giroMedioPorTick);
        
        double futuroX = enemyX;
        double futuroY = enemyY;
        double futuroHeading = headingRad;
        
        double deltaTime = 1;
        for (double t = 0; t * bulletSpeed < Point2D.distance(myX, myY, futuroX, futuroY); t += deltaTime) {
            futuroHeading += giroRadPorTick * deltaTime;
            futuroX += Math.sin(futuroHeading) * velocity * deltaTime;
            futuroY += Math.cos(futuroHeading) * velocity * deltaTime;
            
            futuroX = Math.max(Math.min(futuroX, getBattleFieldWidth() - 18), 18);
            futuroY = Math.max(Math.min(futuroY, getBattleFieldHeight() - 18), 18);
        }
        
        double anguloParaFuturo = Math.toDegrees(Math.atan2(futuroX - myX, futuroY - myY));
        return normalRelativeAngleDegrees(anguloParaFuturo - getGunHeading());
    }
    
    /**
     * Predição com aceleração - considera mudanças de velocidade
     */
    private double predicaoComAceleracao() {
        if (historicoInimigo.size() < 2) {
            return predicaoLinearSimples();
        }
        
        // Implementação simplificada para exemplo
        return predicaoLinearSimples();
    }

    // ===== EVENTO: QUANDO BATE NA PAREDE =====
    public void onHitWall(HitWallEvent e) {
        if (modo1x1) {
            moveDirection = -moveDirection;
        } else {
            direcaoMovimento = -direcaoMovimento;
        }
    }

    // ===== EVENTO: QUANDO BATE EM OUTRO ROBÔ =====
    public void onHitRobot(HitRobotEvent e) {
        if (!modo1x1) {
            // Registra acerto se for o alvo atual
            if (trackName != null && e.getName().equals(trackName)) {
                missedShots = 0;
                lastHitTime = getTime();
                out.println("ACERTOU! Resetando contador de erros.");
            }

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
    }

    // ===== EVENTO: QUANDO ACERTA UM TIRO =====
    public void onBulletHit(BulletHitEvent e) {
        if (!modo1x1 && trackName != null && e.getName().equals(trackName)) {
            missedShots = 0;
            lastHitTime = getTime();
            out.println("TIRO CERTO! Resetando contador de erros.");
        }
    }

    // ===== EVENTO: QUANDO ERRA UM TIRO =====
    public void onBulletMissed(BulletMissedEvent e) {
        if (!modo1x1 && trackName != null) {
            missedShots++;
            out.println("TIRO ERROU! Erros consecutivos: " + missedShots);
            if (missedShots >= MAX_MISSED_SHOTS) {
                trocarAlvo();
            }
        }
    }

    // ===== EVENTO: QUANDO VENCE A PARTIDA =====
    public void onWin(WinEvent e) {
        // Faz uma "dança" girando várias vezes em comemoração
        if (modo1x1) {
            for (int i = 0; i < 50; i++) {
                turnRight(30);
                turnLeft(30);
                ahead(10);
            }
        } else {
            for (int i = 0; i < 20; i++) {
                turnRight(30);
                turnLeft(30);
            }
        }
    }
}