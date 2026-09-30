# MineColonies API Usage

MineColonies API calls are kept in `com.colonybridge.minecolonies`.

APIs used by the mod:

- `IColonyManager.getInstance().getAllColonies()`
- `IColony` identity, name, dimension, center, permissions, active/attack/state/happiness methods
- `ICitizenManager.getCitizens()`, `getMaxCitizens()`, `getCurrentCitizenCount()`
- `ICitizenData` identity, name, UUID, job, home/work buildings, status, health entity, sleep/work/idle flags, skill/happiness/disease/food handlers
- `ICommonRegisteredStructureManager.getBuildings()`
- `ICommonBuilding` position, type, level, prestige
- `IBuilding` display/custom name, built/pending state, assigned citizens, open request tokens
- `IBuildingWorker` only via safe type checks for generic worker metadata where available
- `IWorkManager.getWorkOrders()`
- `IWorkOrder` ID/location/type/current and target level/claimed builder hut/stage
- `IRequestManager.getRequestForToken()`
- `IRequest` generic ID/type/state/parent/children/display/requestable data
- `IDeliverable` item request count/result where the request is item-like
- `IBuilding.getHandlers()` plus `FoodUtils.EDIBLE` for a bounded aggregate of stored edible items

No direct MineColonies NBT parsing is used by the mod.
