package ch.uzh.ifi.hase.soprafs23.game;

import java.util.ArrayList;
import java.util.List;

import ch.uzh.ifi.hase.soprafs23.entity.Player;

class PlayerData { //protected (Package Private)
    private final Player player;
    final String token;
    final String name;
    private Integer score;
    private Hand hand;
    private Decision decision;

    private List<GameObserver> observers;

    PlayerData(Player aPlayer) {
        player = aPlayer;
        token = aPlayer.getToken();
        name = aPlayer.getName();
        observers = new ArrayList<>();
    }

    PlayerData() {
        player = new Player();
        token = null;
        name = null;
        observers = new ArrayList<>();
    }

    public void addObserver(GameObserver o) {
        observers.add(o);
    }

    public void removeObserver(GameObserver o) {
        observers.remove(o);
    }

    //setters and getter-------------------------------------
    public Player getPlayer() {
        return player;
    }


    public void setScore(Integer score) {
        for (GameObserver o : observers) {
            o.playerScoreChanged(player, score);
        }
        this.score = score;
    }

    public int getScore() {
        return score;
    }
    
    public void setNewHand(Hand hand) {
        for (GameObserver o : observers) {
            o.newHand(player, hand);
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

    public void setDecision(Decision d) {
        for (GameObserver o : observers) {
            o.playerDecisionChanged(player, d);
        }
        this.decision = d;
    }

    public Decision getDecision() {
        return decision;
    }
    
}
