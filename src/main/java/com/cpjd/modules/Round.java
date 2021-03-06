package com.cpjd.modules;

import com.cpjd.comms.Responder;
import com.cpjd.models.Card;
import com.cpjd.models.Player;
import com.cpjd.poker.AnalyzeGame;
import com.cpjd.poker.GameResult;
import com.cpjd.poker.HandValue;
import lombok.Getter;
import net.dv8tion.jda.core.EmbedBuilder;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * A round represents the time between dealing the cards and a player winning the money.
 * This class will handle that entire phase.
 *
 * Each round is consisted of several betting phases
 */
class Round {

    private TurnProcessor tp; // Receives turns from the player whose turn it is

    private ArrayList<Player> players; // The list of players in the round
    private Responder responder; // For output

    /**
     * A deck, starting off at 52 cards, shuffled according to
     * atmospheric noise
     */
    private ArrayList<Card> deck;
    /**
     * The cards currently on the table
     */
    private ArrayList<Card> drawn;

    /**
     * This is what the bet is currently at. It resets when new cards are drawn
     */
    private double bet;

    /**
     * This is what the pot is currently at.
     */
    @Getter
    private double pot;

    /**
     * This represents the player whose turn it is
     */
    @Getter
    private Player currentTurn;

    private int elapsedTurns;

    /*
     * This marks the "elapsedTurns" that occurs when all players are folded but one,
     * the last player will be given one turn, and then the round will be ended
     */
    private boolean turnExpire;

    /**
     * The index of the player that began the betting cycle
     */
    private int turnLeader;

    public interface RoundEndListener {
        void roundEnded(int newTurnLeader);
    }

    private RoundEndListener listener;

    void setListener(RoundEndListener listener) {
        this.listener = listener;
    }

    Round(Responder responder, ArrayList<Player> players, int turnLeader) {
        this.responder = responder;
        this.players = players;
        this.drawn = new ArrayList<>();
        tp = new TurnProcessor();
        this.turnLeader = turnLeader;
    }

    /**
     * Begins the round by shuffling the deck, dming players, and initializing the first round
     */
    void begin() {
        if(turnLeader >= players.size()) turnLeader = 0;

        currentTurn = players.get(turnLeader);

        deck = Card.deck();

        // Deal cards
        for(Player p : players) {
            p.setFolded(false);
            p.setCardCycleBet(0);

            p.dealHand(deck.remove(0), deck.remove(0));

            pot += p.withdraw(2);
        }

        responder.begin(2);

        responder.dmHands();

        beginTurn();
    }

    /**
     * Ends the round by finding a winner
     * @param cancelRound true to not process a winner
     */
    void end(boolean cancelRound) {
        if(!cancelRound) {
            AnalyzeGame analyzeGame = new AnalyzeGame();
            GameResult result = analyzeGame.analyzeGame(players, drawn);

            /*
             * Okay, so the round is now over and we have a list of winners, and losers.
             *
             * Poker game winning procedures are rather complex, so I'm going to carefully walk through
             * each step.
             *
             * 1) Firstly, every player gets the amount they wagered removed from their bank account
             */
            for(Player p : players) p.setGameBank(p.getGameBank() - p.getWager());

            ArrayList<Player> winners = result.getWinners();

            // Next, determine the end state. This will determine how money will be distributed. There are three possible end states:
            // 1) All but one player is folded
            // 2) There are some players who haven't wagered as much as the others, because they made it here, they must be all-in and unable
            //    to bet more. They will each be awarded a max pot of 2 times what they put in. More work here.
            // 3) All players have wagered the same amount. Distribute wealth evenly.

            // Case 1
            if(result.isPlayersFolded()) {
                result.getWinners().get(0).deposit(pot);
                responder.postWinners(winners, null, true, pot);
            } else {
                // Case 2
                ArrayList<Player> poorWinners = new ArrayList<>(); // winners who can't win the whole pot
                for(Player winner : winners) {

                    boolean poorPlayer = false;

                    for(Player w2 : winners) {
                        if(winner.getWager() < w2.getWager()) {
                            poorPlayer = true;
                            break;
                        }
                    }

                    if(poorPlayer) poorWinners.add(winner);
                }

                if(poorWinners.size() > 0) {
                    // Distribute wealth specially here
                    for(Player p : poorWinners) {
                        /*
                         * Calculate how much that player can win
                         */
                        // Amount they can win
                        double winAmount = p.getWager() * players.size();

                        p.deposit(winAmount / poorWinners.size());

                        pot -= winAmount / poorWinners.size();
                    }

                    double tempPot = pot;

                    // The rest should be evenly distributed to the non-poor winners
                    for(Player p : winners) {
                        if(!poorWinners.contains(p)) {
                            p.deposit(pot / (winners.size() - poorWinners.size()));
                            tempPot -= pot / (winners.size() - poorWinners.size());
                        }
                    }

                    if(tempPot > 0 && tempPot == pot) {
                        // Sort loser hands
                        result.getLosers().sort(Comparator.comparing(Player::getValue));

                        // The best hand
                        HandValue best = result.getLosers().get(result.getLosers().size() - 1).getValue();

                        ArrayList<Player> otherWinners = new ArrayList<>();

                        for(Player p : result.getLosers()) {
                            if(HandValue.distinguish(best, p.getValue()) == 0) otherWinners.add(p);
                        }

                        // Award remaining of the pot
                        for(Player p : otherWinners) {
                            p.deposit(pot / (otherWinners.size()));
                        }
                    }

                    responder.postWinners(winners, poorWinners, false, pot);
                } else {
                    // Distribute wealth evenly to the players
                    for(Player p : winners) p.deposit(pot / winners.size());

                    responder.postWinners(winners, null, false, pot);
                }
            }


            // Begin the next round
            int newTurnLeader = turnLeader + 1;
            if(turnLeader == players.size()) turnLeader = 0;
            listener.roundEnded(newTurnLeader);
        }
    }

    private void nextBettingPhase() {
        // Reset the cycle betting for each player
        bet = 0;
        for(Player p : players) {
            p.setCardCycleBet(0);
        }

        elapsedTurns = 0;

        // Check if the round needs to end
        if(drawn.size() == 5) {
            end(false);
            return;
        }

        // Determine if all the other players have folded
        boolean allFolded = true;
        for(Player p : players) {
            if(!p.isFolded() && !p.matchesMember(currentTurn.getMember())) {
                allFolded = false;
                break;
            }
        }
        if(allFolded) {
            end(false);
            return;
        }

        // Draw cards
        if(drawn.size() == 0) {
            drawn.add(deck.remove(0));
            drawn.add(deck.remove(0));
            drawn.add(deck.remove(0));
            responder.postDrawn(drawn);
        } else {
            drawn.add(deck.remove(0));
            responder.postDrawn(drawn);
        }

        // Set the new turn leader
        currentTurn = players.get(turnLeader);

        beginTurn();
    }


    /**
     * Begins a user's turn, this will assume that {@link Round#currentTurn} is
     * now taking their turn
     */
    private void beginTurn() {
        // Determine if all the other players have folded
        boolean allFolded = true;
        for(Player p : players) {
            if(!p.isFolded() && !p.matchesMember(currentTurn.getMember())) {
                allFolded = false;
                break;
            }
        }
        if(allFolded) {
            end(false);
            return;
        }

        // Check if the player is folded
        if(currentTurn.isFolded() || currentTurn.isAllIn()) {
            nextTurn();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.blue);
        embed.setTitle(currentTurn.getMember().getNickname()+"'s turn.");
        //embed.addField("Use `bet <amount>`, `match`, `fold`, `check`, or `all in`.", "", false);
        embed.addField("Bank", "$"+(int)(currentTurn.getGameBank() - currentTurn.getWager()), true);
        //embed.addField("Min bet $","$"+(int)(bet - currentTurn.getCardCycleBet()), true);
        //embed.addField("Pot", "$"+(int)pot, true);
        embed.addField("Wagered ", "$"+(int)currentTurn.getWager(), true);
        embed.addField("Call", "$"+(int)((bet - currentTurn.getCardCycleBet())), true);
        responder.getPoker().sendMessage(embed.build()).queue();
    }

    TurnProcessor turn() {
        return tp;
    }

    // wager show ante
    // flopped cards after a fold

    public class TurnProcessor {
        /**
         * Bets the specified amount
         * @param amount the amount to bet
         */
        void bet(double amount) {
            if(amount < 0) return;

            // Verify the player has enough money
            if(currentTurn.getWager() + amount > currentTurn.getGameBank()) {
                if((bet - currentTurn.getCardCycleBet()) < (currentTurn.getGameBank() - currentTurn.getWager())) {
                    EmbedBuilder err = new EmbedBuilder();

                    err.setTitle("Insufficient funds.");

                    err.setColor(Color.red);
                    responder.getPoker().sendMessage(err.build()).queue();
                    return;
                }
            }

            if(amount < bet -  currentTurn.getCardCycleBet()) {
                if((bet - currentTurn.getCardCycleBet()) <= (currentTurn.getGameBank() - currentTurn.getWager())) {
                    EmbedBuilder err = new EmbedBuilder();

                    err.setTitle("Your bet of $"+amount+" does not meet the minimum bet of $"+(bet - currentTurn.getCardCycleBet())+".");

                    err.setColor(Color.red);
                    responder.getPoker().sendMessage(err.build()).queue();
                    return;
                }
            }

            double wager = currentTurn.wager(amount);
            if(wager > bet) {
                bet = wager;
            }
            pot += wager;

            currentTurn.setCardCycleBet(currentTurn.getCardCycleBet() + wager);

            /*
             * Send some feedback.
             */
            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(Color.orange);
            StringBuilder builder = new StringBuilder();

            if(wager != 0) {
                //if(!raised || oldBet == 0)
                    builder.append(currentTurn.getMember().getNickname()).append(" bet $").append((int) wager).append(". Pot: $").append((int) pot).append(".\n");
               // else if(amount - oldBet - currentTurn.getCardCycleBet() != 0) embed.setTitle(currentTurn.getMember().getNickname()+" raised $"+(int)(amount - oldBet - currentTurn.getCardCycleBet())+". Pot: $"+(int)pot+".");
            }
            else builder.append(currentTurn.getMember().getNickname()).append(" checked. Pot: $").append((int) pot).append(".\n");

            if(currentTurn.isAllIn()) builder.append(currentTurn.getMember().getNickname()).append(" is all in!\n");

            embed.setTitle(builder.toString());

            responder.getPoker().sendMessage(embed.build()).queue();

            nextTurn();
        }

        void match() {
            // Check if the call is greater than the user's bank
            if(bet - currentTurn.getCardCycleBet() > (int)(currentTurn.getGameBank() - currentTurn.getWager())) {
                allIn();
                return;
            }

            bet(bet - currentTurn.getCardCycleBet());
        }

        void fold() {
            currentTurn.setFolded(true);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(Color.orange);
            embed.setTitle(currentTurn.getMember().getNickname()+" folded.");
            responder.getPoker().sendMessage(embed.build()).queue();

            nextTurn();
        }

        void allIn() {
            bet(currentTurn.getGameBank() - currentTurn.getWager());
        }

        void check() {
            bet(0);
        }
    }

    /**
     * Proceeds to the next turn, call this after a user makes their play.
     * This method will decide whether to draw cards or issue more turns to the users
     */
    private void nextTurn() {
        elapsedTurns++;

        // set the next player
        int currentTurnIndex = players.indexOf(currentTurn);
        currentTurnIndex++;
        if(currentTurnIndex == players.size()) currentTurnIndex = 0;
        currentTurn = players.get(currentTurnIndex);

        // check if everyone is all in
        boolean allAllin = true;
        for(Player p : players) {
            if(!p.isAllIn() && !p.isFolded()) {
                allAllin = false;
                break;
            }
        }

        if(allAllin && turnExpire) {
            // Ensure that all cards have been drawn
            if(drawn.size() != 5) {
                while(drawn.size() != 5) {
                    drawn.add(deck.remove(0));
                }
                responder.postDrawn(drawn);
            }
            end(false);
            return;
        }

        // check for turn expire
        int allInPlayers = 0;
        int foldedPlayers = 0;
        for(Player p : players) {
            if(p.isAllIn()) allInPlayers++;
            if(p.isFolded()) foldedPlayers++;
        }

        if(allInPlayers == players.size() - foldedPlayers - 1 && !turnExpire) {
            turnExpire = true;
        }

        // check if the next betting phase begins, this will happen when:
        // 1) every player has had at least 1 turn
        // 2) every player's betting cycle meets the current bet
        boolean minimumBetsMet = true;
        for(Player p : players) {
            if(p.isFolded()) continue;

            for(Player p2 : players) {
                if(p2.isFolded()) continue;

                if(p2.matchesMember(p.getMember())) continue;

                if(p.getCardCycleBet() != p2.getCardCycleBet()) {
                    minimumBetsMet = false;
                    break;
                }
            }
        }

        if(elapsedTurns >= players.size() && minimumBetsMet) {
            nextBettingPhase();
        } else {
            beginTurn();
        }
    }
}
