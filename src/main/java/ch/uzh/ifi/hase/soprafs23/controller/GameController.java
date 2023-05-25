package ch.uzh.ifi.hase.soprafs23.controller;

import ch.uzh.ifi.hase.soprafs23.entity.MutablePlayer;
import ch.uzh.ifi.hase.soprafs23.entity.Player;
import ch.uzh.ifi.hase.soprafs23.game.Decision;
import ch.uzh.ifi.hase.soprafs23.game.GamePhase;
import ch.uzh.ifi.hase.soprafs23.game.Hand;
import ch.uzh.ifi.hase.soprafs23.game.HandOwnerWinner;
import ch.uzh.ifi.hase.soprafs23.rest.dto.*;
import ch.uzh.ifi.hase.soprafs23.rest.mapper.DTOMapper;
import ch.uzh.ifi.hase.soprafs23.service.GameService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// add to push
@CrossOrigin(origins = { "http://localhost:3000/", "https://sopra-group-08-client.oa.r.appspot.com/" })
@RestController
public class GameController {

    // Field injection might lead to issues, maybe change to constructor injection
    @Autowired
    SimpMessagingTemplate messagingTemplate;

    private final GameService gameService;

    GameController() {
        this.gameService = new GameService(this);
    }

    @PostMapping("/games")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public String createGame(@RequestBody PlayerDTO playerDTO) {
        MutablePlayer player = DTOMapper.INSTANCE.convertPlayerDTOtoEntity(playerDTO);
        String gameId = gameService.createGame(new Player(player));
        return String.format("{\"id\":\"%s\"}", gameId);
    }

    @GetMapping("/games/{gameId}/lobby")
    @ResponseStatus(HttpStatus.OK)
    public void isLobbyJoinable(@PathVariable String gameId) {
        gameService.isLobbyJoinable(gameId);
    }
    @PostMapping("/games/helpers/playlist")
    @ResponseStatus(HttpStatus.OK)
    public void checkPlaylistUrl(@RequestBody PlaylistDTO playlistDTO){
        gameService.checkPlaylist(playlistDTO.getPlaylistUrl());
    }

    @GetMapping("/games/{gameId}/host")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public PlayerDTO getHost(@PathVariable String gameId) {
        MutablePlayer host = gameService.getHost(gameId);
        return DTOMapper.INSTANCE.convertEntityToPlayerDTO(host);
    }

    
    //send Data to topics. usually data is sent automatically. but if some data got lost this end point can be used to send it again.
    @MessageMapping("/games/{gameId}/sendData")
    public void sendGameData(@DestinationVariable String gameId) {
        gameService.sendGameData(gameId);
    }

    @MessageMapping("/games/{gameId}/resendSettings")
    public void resendSettings(@DestinationVariable String gameId) {
        gameService.resendSettings(gameId);
    }

    //send Data to topics. usually data is sent automatically. but if some data got lost this end point can be used to send it again.
    @MessageMapping("/games/{gameId}/players/{playerToken}/sendHand")
    public void sendHandData(@DestinationVariable String gameId, @DestinationVariable String playerToken) {
        gameService.sendHandData(gameId, playerToken);
    }

    @MessageMapping("/games/{gameId}/players/add")
    @SendTo("/topic/games/{gameId}/players")
    public Collection<PlayerWsDTO> addPlayer(@DestinationVariable String gameId, PlayerDTO playerDTO) {
        try {
            MutablePlayer player = DTOMapper.INSTANCE.convertPlayerDTOtoEntity(playerDTO);
            // add player to game
            gameService.addPlayer(gameId, new Player(player));
            // convert player-list to DTOs
        } catch (ResponseStatusException e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error", new Exception(e.getMessage(), e.getCause()));
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error", e);
        }
        return gameService.getPlayers(gameId);
    }
    

    @MessageMapping("/games/{gameId}/players/remove")
    @SendTo("/topic/games/{gameId}/players")
    public Collection<PlayerWsDTO> removePlayer(@DestinationVariable String gameId, PlayerDTO playerDTO) {
        try {
            // convert DTO to entity
            MutablePlayer player = DTOMapper.INSTANCE.convertPlayerDTOtoEntity(playerDTO);
            // remove player from game
            gameService.removePlayer(gameId, new Player(player));
            // convert player-list to DTOs
        } catch (ResponseStatusException e) {
                    messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error", new Exception(e.getMessage(), e.getCause()));
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error", e);
        }
        return gameService.getPlayers(gameId);
    }

    @MessageMapping("/games/{gameId}/settings")
    @SendTo("/topic/games/{gameId}/settings")
    public SettingsWsDTO updateSettings(@DestinationVariable String gameId, SettingsWsDTO settingsWsDTO) {
        try {
            // update settings
            gameService.setGameSettings(gameId, settingsWsDTO);
            // send new settings to all players
        } catch (ResponseStatusException e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error",
                    new Exception(e.getMessage(), e.getCause()));
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error", e);
        }
        return settingsWsDTO;
    }

    public void sendSettingsToClient(String gameId, SettingsWsDTO settings) {
        messagingTemplate.convertAndSend("/topic/games/"+gameId+"/settings", settings);
    }

    @MessageMapping("/games/{gameId}/start")
    @SendTo("/topic/games/{gameId}/start")
    public String startGame(@DestinationVariable String gameId) {
        // start game
        try {
            gameService.startGame(gameId);
        } catch (ResponseStatusException e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error",
                    new Exception(e.getMessage(), e.getCause()));
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error", e);
        }
        return "Game started.";
    }

    @MessageMapping("/games/{gameId}/noYtApi")
    public void noYtApi(@DestinationVariable String gameId) {
        gameService.noYtApi(gameId);
    } 

    @MessageMapping("/games/{gameId}/players/{playerToken}/decision")
    public void handlePlayerDecision(@DestinationVariable String gameId,
                                     @DestinationVariable String playerToken,
                                     DecisionWsDTO decisionWsDTO)
    {
        try {
            gameService.playerDecision(gameId, playerToken, decisionWsDTO);
        } catch (ResponseStatusException e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error", new Exception(e.getMessage(), e.getCause()));
            gameService.playerUpdate(gameId); //in case client has not received correct playerState
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error", e);
            gameService.playerUpdate(gameId); //in case client has not received correct playerState
        }
    }

    @MessageMapping("/games/{gameId}/rounds/next")
    public void nextRound(@DestinationVariable String gameId) {
        try {
            gameService.nextRound(gameId);
        } catch (ResponseStatusException e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error",
                    new Exception(e.getMessage(), e.getCause()));
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error", e);
        }
    }

    @MessageMapping("/games/{gameId}/close")
    @SendTo("/topic/games/{gameId}/close")
    public void closeGame(@DestinationVariable String gameId) {
        try {
            gameService.closeGame(gameId);
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/close", "Game closed");
        } catch (ResponseStatusException e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error",
                    new Exception(e.getMessage(), e.getCause()));
        } catch (Exception e) {
            messagingTemplate.convertAndSend("/topic/games/" + gameId + "/error", e);
        }
    }

    /** OBSERVER ENDPOINT METHODS
     * these methods are invoked by gameService to send data to the FE*/
    public synchronized void gameStateChanged(String gameId, GameStateWsDTO gameStateWsDTO) {
        String destination = String.format("/topic/games/%s/state", gameId);
        messagingTemplate.convertAndSend(destination, gameStateWsDTO);
    }

    public synchronized void playerStateChanged(String gameId, Collection<PlayerWsDTO> playersDTOList) {
        String destination = String.format("/topic/games/%s/players", gameId);
        messagingTemplate.convertAndSend(destination, playersDTOList);
    }

    public void showdown(String gameId, Collection<HandOwnerWinner> handOwnerWinners) {
        String destination = String.format("/topic/games/%s/showdown", gameId);
        ObjectMapper objectMapper = new ObjectMapper();
        String responseBody;
        try {
            responseBody = objectMapper.writeValueAsString(handOwnerWinners);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not convert showdown to JSON");
        }

        messagingTemplate.convertAndSend(destination, responseBody);
    }

    public void newHand(String gameId, Player player, Hand hand) {
        newHand(gameId, player.getToken(), hand);
    }

    public void newHand(String gameId, String player, Hand hand) {
        String destination = String.format("/topic/games/%s/players/%s/hand", gameId, player);
        ObjectMapper objectMapper = new ObjectMapper();
        String responseBody;
        try {
            responseBody = objectMapper.writeValueAsString(hand.getComments());
        }
        catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not convert hand to JSON");
        }

        messagingTemplate.convertAndSend(destination, responseBody);
    }

    public void newVideoData(String gameId, VideoDataWsDTO videoData) {
        String destination = String.format("/topic/games/%s/video", gameId);
        messagingTemplate.convertAndSend(destination, videoData);
    }

    @MessageMapping("/echo")
    public void echo(String msg) {
        echoResponse(msg);
    }

    public void echoResponse(String msg) {
        messagingTemplate.convertAndSend("/topic/echo", "pong" + msg);
    }

    /**
     * @throws JsonProcessingException
     * @throws MessagingException
     * 
     */
    @MessageMapping("/echoDTO")
    public void settingsEcho() throws MessagingException, JsonProcessingException {

        var player1 = new PlayerWsDTO("playerToken1", "Peter Toggenburger1", 0, Decision.NOT_DECIDED, false, false, false);
        var player2 = new PlayerWsDTO("playerToken2", "Peter Toggenburger2", 100, Decision.NOT_DECIDED, false, false, false);
        Collection<PlayerWsDTO> playerCollection = new ArrayList<>();
        playerCollection.add(player1);
        playerCollection.add(player2);
        ObjectMapper objectMapper = new ObjectMapper();
        var hand = new Hand();
        var handOwnerWinner1 = new HandOwnerWinner();
        handOwnerWinner1.setHand(hand);
        handOwnerWinner1.setPlayer(new Player("playerName1", "playerToken1"));
        handOwnerWinner1.setIsWinner(true);

        var handOwnerWinner2 = new HandOwnerWinner();
        handOwnerWinner2.setHand(hand);
        handOwnerWinner2.setPlayer(new Player("playerName2", "playerToken2"));
        handOwnerWinner2.setIsWinner(false);

        messagingTemplate.convertAndSend("/topic/echoVideoData", new VideoDataWsDTO());
        messagingTemplate.convertAndSend("/topic/echoPlayer", player1);
        messagingTemplate.convertAndSend("/topic/echoPlayerCollection", playerCollection);
        messagingTemplate.convertAndSend("/topic/echoGameState", new GameStateWsDTO(0, 0, false, "playerToken", GamePhase.LOBBY));
        messagingTemplate.convertAndSend("/topic/echoDecision", new DecisionWsDTO());
        messagingTemplate.convertAndSend("/topic/echoSettings", new SettingsWsDTO());
        messagingTemplate.convertAndSend("/topic/echoHand", objectMapper.writeValueAsString(hand.getComments()));
        messagingTemplate.convertAndSend("/topic/echoError", new IllegalStateException("Test error"));
        messagingTemplate.convertAndSend("/topic/echoHandOwnerWinner", objectMapper.writeValueAsString(List.of(handOwnerWinner1,handOwnerWinner2)));
    }
}


