package ch.uzh.ifi.hase.soprafs23.service;

import ch.uzh.ifi.hase.soprafs23.YTAPIManager.YTAPIManager;
import ch.uzh.ifi.hase.soprafs23.controller.GameController;
import ch.uzh.ifi.hase.soprafs23.entity.MutablePlayer;
import ch.uzh.ifi.hase.soprafs23.entity.Player;
import ch.uzh.ifi.hase.soprafs23.game.Decision;
import ch.uzh.ifi.hase.soprafs23.game.Game;
import ch.uzh.ifi.hase.soprafs23.game.GameObserver;
import ch.uzh.ifi.hase.soprafs23.game.GamePhase;
import ch.uzh.ifi.hase.soprafs23.game.Hand;
import ch.uzh.ifi.hase.soprafs23.game.VideoData;
import ch.uzh.ifi.hase.soprafs23.rest.dto.DecisionWsDTO;
import ch.uzh.ifi.hase.soprafs23.rest.dto.PlayerWsDTO;
import ch.uzh.ifi.hase.soprafs23.rest.dto.SettingsWsDTO;
import ch.uzh.ifi.hase.soprafs23.rest.dto.VideoDataWsDTO;
import ch.uzh.ifi.hase.soprafs23.rest.mapper.DTOMapper;

import org.springframework.beans.factory.annotation.Autowired;
// import ch.uzh.ifi.hase.soprafs23.rest.dto.PlayerWsDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

@Service //part of the Spring Framework, and you will use it to mark a class as a service layer component. 
@Transactional // transactions should be managed for this service via @Transactional annotation.

// TODO: start games
// TODO: end/delete games
public class GameService implements GameObserver{

    // @Autowired
    public GameController gameController;
    
    public GameService(GameController gameController) {
        this.gameController = gameController;
    }

    private HashMap<String, Game> games = new HashMap<>();
    //The service maintains a HashMap of games with game IDs as keys and Game objects as values.

    private HashMap<String, GameData> gamesData = new HashMap<>();

    public String createGame(Player host){
        // create new game
        Game newGame = new Game(host);

        GameData gameData = new GameData();
        gameData.playersData.put(host.getToken(), new PlayerWsDTO(host.getToken(),host.getName(),null,null,false,false,false));

        newGame.addObserver(this);
        
        
        // set host (host is not added to player list here)
        // FE sends a WS message to add the host to the player list
        
        
        // add game to list of games
        games.put(newGame.getGameId(), newGame);
        gamesData.put(newGame.getGameId(), gameData);
        
        return newGame.getGameId();
    }

    public void startGame(String gameId) {
        checkIfGameExists(gameId);
        Game game = games.get(gameId);
        try {
            game.startGame();
        }
        // TODO throw more specific exception - no players? other error?
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }


    public void playerDecision(String gameId, String playerId, DecisionWsDTO decisionWsDTO) {
        Decision decision = null;
        Integer raiseAmount = decisionWsDTO.getRaiseAmount();
        // try enum conversion
        try {
            decision = Decision.valueOf(decisionWsDTO.getDecision().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Illegal decision");
        }

        checkIfGameExists(gameId);
        Game game = games.get(gameId);
        try {
            switch (decision) {
                case CALL: game.call(playerId);

                    break;
                case RAISE: game.raise(playerId, raiseAmount);

                    break;
                case FOLD: game.fold(playerId);

                    break;

                default:
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Illegal decision");
            };
        }
        catch (Exception e) {
            
        }

    }

    public void nextRound(String gameId) {
        checkIfGameExists(gameId);
        Game game = games.get(gameId);
        try {
            game.nextRound();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }        
    }

    public MutablePlayer getHost(String gameId){
        if (!games.containsKey(gameId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game with id " + gameId + " does not exist.");
        }
        return new MutablePlayer(games.get(gameId).getHost());
    }

    public Game getGame(String gameId) {
        checkIfGameExists(gameId);
        return games.get(gameId);
    }

    //returns a list of players for a specified game.
    public Collection<PlayerWsDTO> getPlayers(String gameId) {
        checkIfGameExists(gameId);
        return gamesData.get(gameId).playersData.values();
    }

    // adds a new player to a specified game.
    public void addPlayer(String gameId, Player player) {
        // TODO deal with case where player is registered
        // check if game exists
        checkIfGameExists(gameId);


        PlayerWsDTO playerWsDTO = new PlayerWsDTO(player.getToken(),player.getName(),null,null,false,false,false);
        
        gamesData.get(gameId).playersData.put(playerWsDTO.getToken(), playerWsDTO);

        Game game = games.get(gameId);
        try {
            game.setup.joinGame(player);
        }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public void removePlayer(String gameId, Player player) {
        // check if game exists
        checkIfGameExists(gameId);

        Game game = games.get(gameId);
        var gameData = gamesData.get(gameId);
        // TODO allow players to leave after the game has started
        
        try {
            game.setup.leaveGame(player);
            gameData.playersData.remove(player.getToken());
        }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    public void setGameSettings(String gameId, SettingsWsDTO settings) {
        // TODO deal with case where player is registered
        // check if game exists
        checkIfGameExists(gameId);

        Game game = games.get(gameId);

        try {
            game.setup.setBigBlindAmount(settings.getBigBlind());
            game.setup.setSmallBlindAmount(settings.getSmallBlind());
            game.setup.setStartScoreForAll(settings.getInitialBalance());
            game.setup.video.setPlaylist(settings.getPlaylistUrl());
            game.setup.video.setLanguage(settings.getLanguage());
        }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    

    /** OBSERVER METHODS */

    @Override
    public void playerScoreChanged(String gameId, Player player, Integer score) {
        checkIfGameExists(gameId);
        //update GameData
        GameData gameData = gamesData.get(gameId);
        gameData.playersData.get(player.getToken()).setScore(score);

        //send GameData to front end
        gameController.playerStateChanged(gameId, gameData.playersData.values());

    }

    
    @Override
    public void newHand(String gameId, Player player, Hand hand) {
        checkIfGameExists(gameId);
        
        //send GameData to front end
        gameController.newHand(gameId, player, hand); //todo create Setting DTO
    }

    @Override
    public void playerDecisionChanged(String gameId, Player player, Decision decision) {
        checkIfGameExists(gameId);
        //update GameData
        GameData gameData = gamesData.get(gameId);
        PlayerWsDTO playerWsDTO = gameData.playersData.get(player.getToken());
        playerWsDTO.setLastDecision(decision);

        //send GameData to front end
        gameController.playerStateChanged(gameId, gameData.playersData.values()); //todo create Setting DTO

    }

    @Override
    public void currentPlayerChange(String gameId, Player player) {
        checkIfGameExists(gameId);
        //update GameData
        GameData gameData = gamesData.get(gameId);
        gameData.setCurrentPlayer(player);

        //send GameData to front end
        gameController.playerStateChanged(gameId, gameData.playersData.values()); //todo create Setting DTO

    }

    @Override
    public void roundWinnerIs(String gameId, Player player) {
        checkIfGameExists(gameId);
        //update GameData
        GameData gameData = gamesData.get(gameId);
        gameData.gameStateWsDTO.setRoundWinner(player);
        //send GameData to front end
        gameController.gameStateChanged(gameId, gameData.gameStateWsDTO);

    }

    @Override
    public void gameGettingClosed(String gameId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'gameGettingClosed'");
    }

    @Override
    public void gamePhaseChange(String gameId, GamePhase gamePhase) {
        checkIfGameExists(gameId);
        //update GameData
        GameData gameData = gamesData.get(gameId);
        gameData.gameStateWsDTO.setGamePhase(gamePhase);

        //send GameData to front end
        gameController.gameStateChanged(gameId, gameData.gameStateWsDTO);


    }

    @Override
    public void potScoreChange(String gameId, Integer score) {
        checkIfGameExists(gameId);
        //update GameData
        GameData gameData = gamesData.get(gameId);
        gameData.gameStateWsDTO.setCurrentPot(score);

        //send GameData to front end
        gameController.gameStateChanged(gameId, gameData.gameStateWsDTO);
    }

    @Override
    public void callAmountChanged(String gameId, Integer newCallAmount) {
        checkIfGameExists(gameId);
        //update GameData
        GameData gameData = gamesData.get(gameId);
        gameData.gameStateWsDTO.setCurrentBet(newCallAmount);

        //send GameData to front end
        gameController.gameStateChanged(gameId, gameData.gameStateWsDTO);
    }

    @Override
    public void newPlayerBigBlindNSmallBlind(String gameId, Player smallBlind, Player bigBlind) {
        checkIfGameExists(gameId);
        //update GameData
        GameData gameData = gamesData.get(gameId);
        gameData.setSmallBlind(smallBlind);
        gameData.setBigBlind(bigBlind);

        //send GameData to front end
        gameController.playerStateChanged(gameId, gameData.playersData.values());
    }

    @Override
    public void newVideoData(String gameId, VideoData videoData) {
        var vd = new VideoDataWsDTO();
        vd.setDuration(videoData.videoLength);
        vd.setLikes(videoData.likes);
        vd.setReleaseDate(videoData.releaseDate);
        vd.setThumbnailUrl(videoData.thumbnail);
        vd.setTitle(videoData.title);
        vd.setViews(videoData.views);
        gameController.newVideoData(gameId, vd);
        throw new UnsupportedOperationException("Unimplemented method 'newVideoData'");
    }

    /** HELPER METHODS */

    public boolean checkPlaylist(String URL) throws Exception {//true if playlist contains 6 or more videos
        return YTAPIManager.checkPlaylistUrl(URL);
    }

    private void checkIfGameExists(String gameId) {
        if (!games.containsKey(gameId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game with id " + gameId + " does not exist.");
        }
        
    }

}
