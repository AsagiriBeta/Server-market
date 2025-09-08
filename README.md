# Server Market Mod Guide | [中文文档](./README_ZH.md)

## Introduction
This mod adds a complete player economy and item trading market to Minecraft servers. It supports balance transfers, item listing, global market search, a system shop, and physical currency mapping between items and balance.

## Main Commands

### Player Commands
- **/money**  
  Check current balance.

- **/mpay <player> <amount>**  
  Transfer money to another player.  
  Example: `/mpay Steve 100.5`

- **/mprice <price>**  
  Set the sale price for the held item in your personal shop.  
  Example: `/mprice 5.0`

- **/msell <quantity>**  
  Restock the held item to your personal shop.  
  Example: `/msell 32`

- **/mpull**  
  Unlist the held item and retrieve its stock.

- **/msearch <itemID>**  
  Search the global market for an item.  
  Example: `/msearch minecraft:diamond`

- **/mbuy <quantity> <itemID> [seller]**  
  Buy items from the market. Without seller it auto-matches the lowest price across all sellers. If a seller is specified, only buys from that seller (use `server` for system shop, or a seller's name if required by your setup).  
  Examples:  
  - `/mbuy 3 minecraft:emerald` (auto match)  
  - `/mbuy 5 minecraft:diamond server` (force system shop)  
  - `/mbuy 2 minecraft:iron_ingot Steve` (specific seller)
  Notes:  
  - System items may have a per-player per-day purchase limit (see /aprice).  
  - Purchase splits across multiple listings until quantity is satisfied.

- **/mlist [player|server]**  
  View listed items for a player or the system shop.  
  Example: `/mlist Steve` or `/mlist server`

- **/mcash <value> <quantity>**  
  Redeem balance into physical currency items according to configured face value mappings.  
  Example: `/mcash 10 5`  
  Notes:
  - The value must be configured by admins via `/acash`.
  - Requires sufficient balance; items are given respecting max stack size.
  - If the currency mapping has custom NBT, it will be applied to the issued items.

- **/mexchange <quantity>**  
  Exchange physical currency items held (and matching items in inventory) back into balance.  
  Example: `/mexchange 16`  
  Notes:
  - Hold the currency item in your main hand; quantity counts across inventory stacks that match the same signature (Item ID + CUSTOM_DATA).

### Admin Commands (Requires OP Level 4)
- **/mset <player> <amount>**  
  Set a player's balance.  
  Example: `/mset Steve 1000.0`

- **/aprice <price> [limitPerDay]**  
  Set system shop price for the held item. Adds optional per-player daily purchase limit. Omit or use -1 for unlimited daily purchases (stock itself is infinite).  
  Examples:  
  - `/aprice 8.5` (no daily limit)  
  - `/aprice 12.0 32` (each player can buy at most 32 per day)  
  Notes:  
  - limitPerDay counts per player per real-world day (server local date).  
  - Changing price or limit re-applies settings.  
  - If the item was not previously in the system shop it is added automatically.

- **/apull**  
  Unlist the held item from the system shop.

- **/mlang <language>**  
  Switch system language.  
  Example: `/mlang en` or `/mlang zh`

- **/acash <value>**  
  Mark the held item as a currency with the given face value (short form supported).  
  Example: `/acash 10.0`

- **/acash get**  
  Show the face value configured for the held item.

- **/acash del**  
  Remove the currency mapping for the held item.

- **/acash list [itemID]**  
  List all currency mappings; optionally filter by item ID.  
  Example: `/acash list minecraft:diamond`

- **/mreload**  
  Reload configuration (including language from config if present).

## Market System Features
1. **Dual Markets**
   - Player Market: Player-set prices, limited stock.
   - System Market: Admin-controlled, unlimited stock (with optional per-player daily caps).

2. **Transactions**
   - Auto-match the lowest price.
   - Merge across multiple sellers' inventories.
   - Optional seller-specific purchase.

## Advanced: Physical Currency (/acash) and Player Exchange
- Identification: Distinguished by "Item ID + NBT (CUSTOM_DATA)"; if no NBT, item ID only.
- Admin operations:
  - Set: `/acash <value>` (hold target item)
  - Check: `/acash get`
  - Delete: `/acash del`
  - List: `/acash list [itemID]`
- Player operations:
  - `/mcash` to convert balance to physical currency items.
  - `/mexchange` to convert currency items back into balance.
- Security tip: Prefer items with unique custom data to reduce counterfeiting risk.
