# Falling Blocks

This module detects groups of blocks that have become disconnected from the ground, and breaks them. More specifically, it counts as solid those blocks which have the attachmentAllowed attribute (although this is likely to change in the future), and a solid block is disconnected from the ground if there is no path from it to an unloaded chunk entirely through solid blocks.

## Internals

It uses an octree data structure to keep track of connected components of solid blocks, which is generated fresh every time the world is loaded rather than being saved. Because of its sparsity, the data structure is smaller than a block data field. The integrity of the datastructure can be verified using the fallingBlocksDebug command. In multiplayer, the module only runs serverside.