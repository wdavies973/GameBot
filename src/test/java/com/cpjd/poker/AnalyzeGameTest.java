package com.cpjd.poker;

import com.cpjd.models.Card;
import com.cpjd.models.Number;
import com.cpjd.models.Player;
import com.cpjd.models.Suit;
import org.junit.Test;
import org.omg.PortableInterceptor.DISCARDING;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class AnalyzeGameTest {

    @Test
    public void analyzeGame() {
        // For testing faulty win configurations
        ArrayList<Player> players = new ArrayList<>();

        Player will = new Player("Will", card(Suit.HEARTS, Number.KING), card(Suit.CLUBS, Number.TWO));
        Player sam = new Player("Sam", card(Suit.DIAMONDS, Number.TEN), card(Suit.DIAMONDS, Number.ACE));
        //Player alex = new Player("Alex", card(Suit.SPADES, Number.JACK), card(Suit.CLUBS, Number.THREE));

        players.add(sam);
        players.add(will);
        //players.add(alex);

        ArrayList<Card> deck = cards(card(Suit.HEARTS, Number.ACE), card(Suit.DIAMONDS, Number.TWO), card(Suit.HEARTS, Number.SIX)
        ,card(Suit.DIAMONDS, Number.THREE), card(Suit.DIAMONDS, Number.JACK));

        AnalyzeGame analyzeGame = new AnalyzeGame();

        GameResult result = analyzeGame.analyzeGame(players, deck);

        assertTrue(result.getWinners().contains(will));
    }

    // Utils

    private Card card(Suit suit, Number number) {
        return new Card(suit, number);
    }

    private ArrayList<Card> cards(Card... cards) {
        if (cards == null) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(cards));
    }
}