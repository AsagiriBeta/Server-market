# Server Market Mod Guide

## Mod Introduction
This mod adds a player economy system and item trading market to Minecraft servers, supporting features like transfers, trading, item listing, global market search, etc.

## Main Commands

### Player Commands
- **/money**  
  Check current balance

- **/mpay <player> <amount>**  
  Transfer money to another player  
  Example: `/mpay Steve 100.5`

- **/mprice <price>**  
  Set sale price for held item  
  Example: `/mprice 5.0`

- **/msell <quantity>**  
  Restock held item to personal shop  
  Example: `/msell 32`

- **/mpull**  
  Remove held item from market

- **/msearch <itemID>**  
  Search global sales  
  Example: `/msearch minecraft:diamond`

- **/mbuy <quantity> <itemID>**  
  Buy items from market  
  Example: `/mbuy 3 minecraft:emerald`

- **/mlist [player/server]**  
  View listed items from player or system  
  Example: `/mlist Steve` or `/mlist server`

### Admin Commands (Requires OP Level 4)
- **/mset <player> <amount>**  
  Set player's balance  
  Example: `/mset Steve 1000.0`

- **/aprice <price>**  
  Set system shop price for held item  
  Example: `/aprice 8.5`

- **/apull**  
  Remove held item from system shop

- **/mlang <language>**  
  Switch system language  
  Example: `/mlang en` or `/mlang zh`

- **/acash <value>**  
  Mark held item as "cash" with a face value (short form supported)  
  Example: `/acash 10.0`

- **/acash get**  
  Query the face value set for the held item

- **/acash del**  
  Delete the face value mapping for the held item

- **/acash list [itemID]**  
  List items mapped as "cash", optionally filter by item ID  
  Example: `/acash list minecraft:diamond`

- **/mreload**  
  Reload configuration (language changes in config take effect)

## Market System Features
1. **Dual Markets**
   - Player Market: Player-set prices, limited stock
   - System Market: Admin-controlled, unlimited stock

2. **Transaction Process**
   - Auto-match lowest price
   - Cross-player inventory merging

## Advanced: Physical Currency (/acash)
- Identification: Distinguished by "Item ID + NBT (CUSTOM_DATA)"; if no NBT, item ID only.
- Common use: Assign a fixed face value to specific items as in-server currency.
- Admin ops:
  - Set: `/acash <value>` (hold target item)
  - Check: `/acash get`
  - Delete: `/acash del`
  - List: `/acash list [itemID]`
- Security tip: Prefer items with unique custom data to reduce counterfeiting risk.

[中文文档](./README.md)
