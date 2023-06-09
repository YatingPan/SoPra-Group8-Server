package ch.uzh.ifi.hase.soprafs23.game;

import java.util.ArrayList;
import java.util.List;

import ch.uzh.ifi.hase.soprafs23.entity.Player;

class PlayerData { //protected (Package Private)
    private final Player player;
    final String token;
    final String name;
    private String gameId;
    private Integer score = 0;
    private Integer scorePutIntoPot = 0;
    private Hand hand;
    private Decision decision = Decision.NOT_DECIDED;

    private List<GameObserver> observersPlayer;

    PlayerData(Player aPlayer) {
        player = aPlayer;
        token = aPlayer.getToken();
        name = aPlayer.getName();
        observersPlayer = new ArrayList<>();
    }

    PlayerData() {
        player = new Player();
        token = null;
        name = null;
        observersPlayer = new ArrayList<>();
    }

    public void addObserver(String gameId, GameObserver o) {
        synchronized (observersPlayer) {
            this.gameId = gameId;
            observersPlayer.add(o);
        }
    }

    public void removeObserver(GameObserver o) {
        synchronized (observersPlayer){
            observersPlayer.remove(o);
        }
    }

    //setters and getter-------------------------------------

    public Integer getScorePutIntoPot() {
        return scorePutIntoPot;
    }

    public synchronized void setScorePutIntoPot(Integer scorePutIntoPot) {
        for(var o : observersPlayer){
            o.updatePlayerPotScore(gameId, player, scorePutIntoPot);
        }
        this.scorePutIntoPot = scorePutIntoPot;
    }

    public Player getPlayer() {
        return player;
    }


    public synchronized void setScore(Integer score) {
        if (score == null) {
            return;
        }
        if(this.score != null && score.compareTo(this.score) == 0) {
            return;
        }
            
        for (GameObserver o : observersPlayer) {
            o.playerScoreChanged(gameId, player, score);
        }
        this.score = score;
    }

    public int getScore() {
        return score;
    }
    
    public synchronized void setNewHand(Hand hand) {
        if (hand == this.hand) {
            return;}
        for (GameObserver o : observersPlayer) {
            o.newHand(gameId, player, hand);
        }
        this.hand = hand;
    }

    // public void setEvaluatedHand(Hand hand) {
    //     for (GameObserver o : observers) {
    //         o.handEvaluated(player, hand);
    //     }
    //     this.hand = hand;
    // }

    public Hand getHand() {
        return hand;
    }

    public synchronized void setDecision(Decision d) {
        if (d == this.decision) {
            return;}
        for (GameObserver o : observersPlayer) {
            o.playerDecisionChanged(gameId, player, d);
        }
        this.decision = d;
    }

    public Decision getDecision() {
        return decision;
    }
    
}
