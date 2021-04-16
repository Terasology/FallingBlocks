// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.fallingblocks.ecs;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.engine.logic.location.LocationComponent;
import org.terasology.engine.physics.engine.PhysicsEngine;
import org.terasology.engine.registry.In;

/**
 * Predicts the falling motion of block group entities in order to make the animation smoother
 * on remote clients.
 */
@RegisterSystem(RegisterMode.REMOTE_CLIENT)
public class FallingPredictionSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    @In
    private EntityManager entityManager;

    @Override
    public void update(float delta) {
        for (EntityRef entity : entityManager.getEntitiesWith(DetachedBlockGroupComponent.class, LocationComponent.class)) {
            LocationComponent location = entity.getComponent(LocationComponent.class);
            DetachedBlockGroupComponent blockGroup = entity.getComponent(DetachedBlockGroupComponent.class);
            if (blockGroup.dying > 0) {
                continue;
            }
            blockGroup.velocity.add(0, -PhysicsEngine.GRAVITY * (1 - blockGroup.support / blockGroup.mass) * delta, 0);
            Vector3fc oldPosition = location.getWorldPosition(new Vector3f());
            location.setWorldPosition(blockGroup.velocity.mul(delta, new Vector3f()).add(oldPosition));

            entity.saveComponent(blockGroup);
            entity.saveComponent(location);
        }
    }
}
