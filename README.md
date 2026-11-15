# Trade Finder

A RuneLite plugin that matches Old School RuneScape buy and sell orders between players, so two
players can trade face to face and skip the 2% Grand Exchange tax.

Not affiliated with or endorsed by Jagex. Old School RuneScape is a trademark of Jagex Ltd.

## How a deal goes

1. Turn on **Connect to Trade Finder server** in the plugin settings (it is off by default) and
   accept RuneLite's third-party server warning.
2. Post a buy or sell order in the side panel: item, quantity and limit price. The panel shows the
   current deal zone (the OSRS Wiki real-time prices) and warns when your price is outside it.
3. When another player has the opposite order for the same item, and both of you are logged in, the
   two orders become a deal. The quantities do not have to be equal: the deal trades the smaller
   remaining amount, and the rest of the larger order stays open and can be filled in parts by later
   deals. An order is in one deal at a time. The older order's price wins.
4. Meet your partner at the east side of the Grand Exchange within 15 minutes. Members meet on world
   302 and free-to-play players on world 301; each has backup worlds. If the meeting world is full,
   press **World full** and the deal moves to the next backup world. The panel shows whether your
   partner is nearby, and the partner is outlined in orange in the game.
5. Open a trade with your partner. A box next to the trade window checks both offers against the
   deal, and turns red with a warning next to the Accept button when they do not match.
6. While the trade window with your partner is open, the plugin shows one clickable line in the
   "Enter amount" prompt (for example after Offer-X) with the amount still missing for the deal. It
   fills the prompt only when you click that line; you still press Enter yourself. Nothing is typed or
   submitted for you.
7. After "Accepted trade." each plugin reports what changed hands. When both reports agree, the deal
   is completed.
8. If the 15 minutes run out, only the player who did not show up loses their order and gets a
   no-show. A player who was at the meeting spot (seen by the partner, or on the meeting world near
   the Grand Exchange) keeps their order: it is open again and can be matched with someone else.

## What it never does

- No automation. It never trades, walks, banks or clicks for you; you do all of that yourself.
- The **Hop** button on the deal card hops to the meeting world once per click, the same way
  RuneLite's World Hopper does. It never hops on its own.
- The amount line in the "Enter amount" prompt writes into that prompt only when you click it. It
  never presses Enter and never touches the chat input.
- The trade check only reads the trade screens and draws on top of them. It does not block or change
  any click.
- No chat. The plugin never clicks, walks, trades or types by itself. What it does on a click of
  yours is exactly two things: one world hop (the Hop button) and filling the "Enter amount" prompt
  (the amount line); you press Enter yourself.

## Order slots and cooldowns

- 5 order slots per player (open and matched orders together). The Orders tab shows how many are used.
- Posting is paused for 10 minutes after you cancel a deal or miss a meeting. The panel shows a
  countdown under the Post buttons. Cancelling an order that has not matched costs nothing, and
  cancelling a deal is free once the partner has not shown up in time and you are at the meeting spot. The panel asks before it
  cancels a deal and says which of the two applies.
- If you were at the meeting spot but your partner never saw you, three meetings in a row, that order
  is cancelled and posting is paused for an hour. Being seen, or completing a deal, resets the count.
- At most 10 new accounts can register from one network address per hour; accounts the server already
  knows are never refused.
- The deal card shows your partner's deals done, no-shows and cancels.

## Privacy

Nothing is sent until you turn on **Connect to Trade Finder server**. Once it is on, the plugin
talks to the Trade Finder server at `api.osrstrade.app` and to no other server; the address
is fixed in the plugin and is not a setting. Connecting to it reveals your
IP address to that server (and to the network service in front of it, which carries your traffic).

What the plugin sends, and when:

| Data | When |
| --- | --- |
| Account hash (the number RuneLite uses to identify your account, not your login) and your RSN | When you are logged in, to register a session |
| Your orders: item, side, quantity, limit price, and whether the item is members-only | When you post an order (cancelling sends the order's number) |
| Your current world, whether you are near the Grand Exchange, and whether your account has membership | About every 30 seconds while logged in and connected, and when any of them changes |
| That the trade window with your deal partner opened, or closed without being accepted | When it happens, during a deal |
| What you gave and received (item ids and quantities, from your inventory before and after) | After "Accepted trade." in a trade with your deal partner |
| That you pressed **World full**, and the world the deal was on | When you press the button |
| That your deal partner is in view at the meeting spot (the deal number only) | Once per deal, again after a world change |
| The item you picked and the limit price you typed | In the Buy or Sell tab, to get the deal zone and the price warning |
| Your IP address | By connecting to the server, with every request and while the connection is open |

Every request after registering also carries a session token, which the server issues when you
register. It is kept in memory only and is never written to disk.

What your trade partner sees about you: your RSN, your deal counters (deals done, no-shows and
cancels), and whether you are at the meeting spot (the deal card says "at the GE" once you are on the
meeting world near the Grand Exchange). Nothing else about you is shown to them.

The plugin does not send the other player's name from the game, your chat, your inventory as a
whole, your bank, or anything about trades that are not with your deal partner.

What the server stores: your RSN and a salted hash of your account hash, a hash of the session
token, your counters (deals done, no-shows, reports) and a time-stamped record of each cancel and
no-show, your orders, the deals made from them, whether each side was seen at the meeting spot, and
the items each side reported giving and receiving. Presence (online, world, near the Grand Exchange)
is held only while you are connected.

Your IP address is used in memory to limit new registrations per address. In addition, the server
keeps request logs that include IP addresses, in size-limited rotating log files, for debugging and
abuse handling. These logs are not linked to the stored player record.

Contact: Discord `vladibeer`

## Building

```bash
./gradlew build   # compile and run the tests
./gradlew run     # start a RuneLite dev client with this plugin loaded
```

The Trade Finder server is a separate project and is not part of this repository.

## License

BSD 2-Clause, see `LICENSE`.
