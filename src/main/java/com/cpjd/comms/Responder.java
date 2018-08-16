package com.cpjd.comms;

import com.cpjd.models.Card;
import com.cpjd.models.Player;
import com.cpjd.poker.GameResult;
import lombok.Getter;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;

/**
 * Responder is used for any sort of feedback to the user(s)
 *
 * @author Will Davies
 */
public class Responder {

    @Getter
    private TextChannel poker;
    private ArrayList<Player> players;

    public Responder(TextChannel poker, ArrayList<Player> players) {
        this.poker = poker;
        this.players = players;
    }

    public void post(String message) {
        poker.sendMessage(message).queue();
    }

    public void postDrawn(ArrayList<Card> drawn) {
        post("Computing...");

        String y = "Now the flop.";
        if(drawn.size() != 3) y = "Next card.";

        Message m = new MessageBuilder().append(y).build();
        poker.sendFile(Card.combine(true, drawn.toArray(new Card[0])), m).queue();
    }

    /**
     * Post winning information, purely informational method, no winning code is handled here
     * @param gameResult
     * @param pot
     */
    public void postWinners(GameResult gameResult, double pot) {
        if(gameResult.getWinners().size() == 1) { // Only 1 winner
            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(Color.magenta);
            embed.addField(gameResult.getWinners().get(0).getMember().getNickname()+" won the pot of $"+(int)pot+"!", "", false);
            StringBuilder info = new StringBuilder();
            for(Player p : players) {
                info.append(p.getMember().getNickname()).append("'s Hand: ").append(p.getCard1().toString()).append(", ").append(p.getCard2().toString()).append("\n");
            }
            // Only add the hands if the game winner wasn't result of folding
            if(!gameResult.isPlayersFolded()) embed.addField("Hands", info.toString(), false);

            poker.sendMessage(embed.build()).queue();
        } else { // More than 1 winner
            StringBuilder players = new StringBuilder();
            for(Player p : gameResult.getWinners()) {
                players.append(p.getMember().getNickname()).append(", ");
            }

            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(Color.magenta);
            embed.addField("Tie! Winners: "+players.toString()+" each won $"+pot / gameResult.getWinners().size()+".", "", false);
            StringBuilder info = new StringBuilder();
            for(Player p : gameResult.getWinners()) {
                info.append(p.getMember().getNickname()).append("'s Hand: ").append(p.getCard1().toString()).append(", ").append(p.getCard2().toString()).append("\n");
            }
            embed.addField("Hands", info.toString(), false);

            poker.sendMessage(embed.build()).queue();
        }
    }

    public void begin(double ante, Player first) {
        EmbedBuilder round = new EmbedBuilder();
        round.setColor(Color.white);
        round.setTitle("NEW ROUND");
        poker.sendMessage(round.build()).queue();

        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(Color.orange);
        builder.setTitle("Everyone antes $"+(int)ante+". Pot: $"+(int)(ante * players.size())+".");
        poker.sendMessage(builder.build()).queue();
    }

    public void dmHands() {
        Player will = players.get(0);

        for(Player p : players) {
            Message message = new MessageBuilder().append(p.getMember().getNickname()).append(", your hand is: ").append(p.getCard1().toString()).append(", ").append(p.getCard2().toString()).append(".").build();

            File hand = Card.combine(false, p.getCard1(), p.getCard2());

            p.getMember().getUser().openPrivateChannel().queue((channel) ->
                    channel.sendFile(hand, message).queue());
        }
    }

}
