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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

@Service //part of the Spring Framework, and you will use it to mark a class as a service layer component. 
@Transactional // transactions should be managed for this service via @Transactional annotation.

public class GameService implements GameObserver{

    // @Autowired
    public final GameController gameController;
    
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
        synchronized (games) {
            games.put(newGame.getGameId(), newGame);
        }
        synchronized (gamesData) {
            gamesData.put(newGame.getGameId(), gameData);
        }
        
        return newGame.getGameId();
    }

    public void startGame(String gameId) throws IOException{
        
        Game game = getGame(gameId);
        try {
            game.startGame();
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
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

        Game game = getGame(gameId);
        switch (decision) {
            case CALL: game.call(playerId);

                break;
            case RAISE: game.raise(playerId, raiseAmount);

                break;
            case FOLD: game.fold(playerId);

                break;

            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Illegal decision");
        }
    }

    public void nextRound(String gameId){
        Game game = getGame(gameId);
        try {
            game.nextRound();
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }        
    }

    public boolean isLobbyJoinable(String gameId) {
        var game = getGame(gameId);

        if (game.getGamePhase() != GamePhase.LOBBY) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Game with id " + gameId + " is not in Lobby phase. (Can't be joined)");
        }
        if (game.setup.getPlayers().size() >= 6) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Game with id " + gameId + " is already full " + game.getPlayers().size() + "/6");
        }
        
        return true;
    }

    public MutablePlayer getHost(String gameId){
        if (!games.containsKey(gameId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game with id " + gameId + " does not exist.");
        }
        return new MutablePlayer(games.get(gameId).getHost());
    }

    private Game getGame(String gameId) {
        checkIfGameExists(gameId);
        return games.get(gameId);
    }

    private GameData getGameData(String gameId) {
        checkIfGameExists(gameId);
        return gamesData.get(gameId);
    }

    //returns a list of players for a specified game.
    public Collection<PlayerWsDTO> getPlayers(String gameId) {
        return getGameData(gameId).playersData.values();
    }

    // adds a new player to a specified game.
    public synchronized void addPlayer(String gameId, Player player) {

        if ((player == null) || (player.getToken() == null)) {
            throw new IllegalStateException("Player can't be null");
        }
        PlayerWsDTO playerWsDTO = new PlayerWsDTO(player.getToken(),player.getName(),null,Decision.NOT_DECIDED,false,false,false);
        Game game = getGame(gameId);
        GameData gameData = getGameData(gameId);

        try {
            synchronized (game) {
                synchronized (gameData) {
                    game.setup.joinGame(player);
                    gameData.playersData.put(playerWsDTO.getToken(), playerWsDTO); //only add if join was successful
                }
            }
        }
        catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public void removePlayer(String gameId, Player player) {
        Game game = getGame(gameId);
        var gameData = gamesData.get(gameId);
        
        if (gameData.gameStateWsDTO.getGamePhase() == GamePhase.LOBBY) {
            try {
                synchronized (game) {
                    synchronized (gameData) {
                        game.setup.leaveGame(player);
                        gameData.playersData.remove(player.getToken());
                    }
                }
            }
            catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
            }
        }else if(gameData.gameStateWsDTO.getGamePhase() != GamePhase.CLOSED){
            try {
                synchronized (game) {
                    synchronized (gameData) {
                        game.leave(player);
                        gameData.playersData.remove(player.getToken());
                    }
                }
            } catch (Exception e){
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
            }
        }
        
    }

    public void setGameSettings(String gameId, SettingsWsDTO settings) {

        Game game = getGame(gameId);
        var gameData = getGameData(gameId);
        gameData.settings = settings;

        try {
            if (settings.getBigBlind() != null) {
                game.setup.setBigBlindAmount(settings.getBigBlind());
            }
            if (settings.getSmallBlind() != null) {
                game.setup.setSmallBlindAmount(settings.getSmallBlind());
            }
            if (settings.getInitialBalance() != null) {
                game.setup.setStartScoreForAll(settings.getInitialBalance());
            }
            if (settings.getPlaylistUrl() != null && settings.getPlaylistUrl().length() != 0) {
                game.setup.video.setPlaylist(settings.getPlaylistUrl());
            }
            if (settings.getLanguage() != null) {
                game.setup.video.setLanguage(settings.getLanguage());
            }
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
        // gameController.playerStateChanged(gameId, gameData.playersData.values());
    }

    
    @Override
    public void newHand(String gameId, Player player, Hand hand) {
        checkIfGameExists(gameId);
        
        var gameData = getGameData(gameId);
        gameData.handData.put(player.getToken(), hand);
        //send GameData to front end
        gameController.newHand(gameId, player, hand);
    }

    @Override
    public void playerDecisionChanged(String gameId, Player player, Decision decision) {
        checkIfGameExists(gameId);
        //update GameData
        GameData gameData = gamesData.get(gameId);
        PlayerWsDTO playerWsDTO = gameData.playersData.get(player.getToken());
        playerWsDTO.setLastDecision(decision);

        //send GameData to front end
        // gameController.playerStateChanged(gameId, gameData.playersData.values()); 

    }

    @Override
    public void currentPlayerChange(String gameId, Player player) {
        //update GameData
        GameData gameData = getGameData(gameId);
        gameData.setCurrentPlayer(player);

        //send GameData to front end
        gameController.playerStateChanged(gameId, gameData.playersData.values()); 

    }

    @Override
    public void roundWinnerIs(String gameId, Player player) {
        //update GameData
        GameData gameData = getGameData(gameId);
        Game game = getGame(gameId);
        gameData.gameStateWsDTO.setRoundWinnerToken(player.getToken());
        //send GameData to front end

        if (player.getToken() == null) { //do not send null updates
            return;
        }

        gameController.gameStateChanged(gameId, gameData.gameStateWsDTO);
        gameController.showdown(gameId, game.getHands());
        gameController.playerStateChanged(gameId, gameData.playersData.values());
    }

    @Override
    public void gameGettingClosed(String gameId) {
        GameData gameData = getGameData(gameId);
        gameData.gameStateWsDTO.setGamePhase(GamePhase.CLOSED);
        gameController.gameStateChanged(gameId, gameData.gameStateWsDTO);
    }

    @Override
    public void gamePhaseChange(String gameId, GamePhase gamePhase) {
        //update GameData
        GameData gameData = getGameData(gameId);
        gameData.gameStateWsDTO.setGamePhase(gamePhase);

        //send GameData to front end
        gameController.gameStateChanged(gameId, gameData.gameStateWsDTO);


    }

    @Override
    public void potScoreChange(String gameId, Integer score) {
        //update GameData
        GameData gameData = getGameData(gameId);
        gameData.gameStateWsDTO.setCurrentPot(score);

        //send GameData to front end
        gameController.gameStateChanged(gameId, gameData.gameStateWsDTO);
    }

    @Override
    public void callAmountChanged(String gameId, Integer newCallAmount) {
        //update GameData
        GameData gameData = getGameData(gameId);
        gameData.gameStateWsDTO.setCurrentBet(newCallAmount);

        //send GameData to front end
        gameController.gameStateChanged(gameId, gameData.gameStateWsDTO);
    }

    @Override
    public void newPlayerBigBlindNSmallBlind(String gameId, Player smallBlind, Player bigBlind) {
        //update GameData
        GameData gameData = getGameData(gameId);
        gameData.setSmallBlind(smallBlind);
        gameData.setBigBlind(bigBlind);

        //send GameData to front end
        // gameController.playerStateChanged(gameId, gameData.playersData.values());
    }

    @Override
    public void newVideoData(String gameId, VideoData videoData) {
        var vd = new VideoDataWsDTO();
        vd.setDuration(videoData.videoLength != null ? videoData.videoLength.toString() : null);
        vd.setLikes(videoData.likes);
        vd.setReleaseDate(videoData.releaseDate != null ? videoData.releaseDate.toString() : null);
        vd.setThumbnailUrl(videoData.thumbnail);
        vd.setTitle(videoData.title);
        vd.setViews(videoData.views);
        var gameData = getGameData(gameId);
        gameData.videoData = vd;
        gameController.newVideoData(gameId, vd);
    }

    /** HELPER METHODS 
     * @throws InterruptedException
     * @throws IOException 
     * @throws IllegalStateException
     * */

    public boolean checkPlaylist(String url) throws ResponseStatusException {//true if playlist contains 6 or more videos
        try {
            YTAPIManager.checkPlaylistUrl(url);
            return true;
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void checkIfGameExists(String gameId) {
        synchronized (games) {
            if (!games.containsKey(gameId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game with id " + gameId + " does not exist.");
            }
        }
    }

    public void closeGame(String gameId) {
        //maybe add some check to not close any game at some time??
        var game = getGame(gameId);
        game.closeGame();
        synchronized (games) {
            games.remove(gameId);
        }
        synchronized (gamesData) {
            gamesData.remove(gameId);
        }
    }

    public void playerUpdate(String gameId) {
        GameData gameData = getGameData(gameId);
        //send GameData to front end
        gameController.playerStateChanged(gameId, gameData.playersData.values());
    }

    public void sendHandData(String gameId, String playerToken) {
        var gameData = getGameData(gameId);
        var hand = gameData.handData.get(playerToken);
        gameController.newHand(gameId, playerToken, hand);
    }

    public void sendGameData(String gameId) {
        var gameData = getGameData(gameId);
        gameController.playerStateChanged(gameId, gameData.playersData.values());
        gameController.gameStateChanged(gameId, gameData.gameStateWsDTO);
        gameController.newVideoData(gameId, gameData.videoData);
    }

    public void resendSettings(String gameId) {
        var gameData = getGameData(gameId);
        gameController.sendSettingsToClient(gameId, gameData.settings);
    }

    @Override
    public void updatePlayerPotScore(String gameId, Player player, Integer scorePutIntoPot) {
        var gameData = getGameData(gameId);
        gameData.playersData.get(player.getToken()).setScorePlayerPutIntoPot(scorePutIntoPot);
        //since this always changes if a player also does a decision. No update is sent to FE
    }

    @Override
    public void playerLeft(String gameId, Player p) {
        var gameData = getGameData(gameId);
        gameData.playersData.remove(p.getToken());
        gameController.playerStateChanged(gameId, gameData.playersData.values());
    }

    public void noYtApi(String gameId) {
        var game = getGame(gameId);
        game.setup.video.useYtApi(false);
    }

}

