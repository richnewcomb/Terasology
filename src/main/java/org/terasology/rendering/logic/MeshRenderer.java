/*
 * Copyright 2013 Moving Blocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.logic;

import com.bulletphysics.linearmath.Transform;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.asset.Assets;
import org.terasology.engine.CoreRegistry;
import org.terasology.entitySystem.EntityRef;
import org.terasology.entitySystem.RegisterMode;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.lifecycleEvents.BeforeDeactivateComponent;
import org.terasology.entitySystem.lifecycleEvents.OnActivatedComponent;
import org.terasology.entitySystem.lifecycleEvents.OnChangedComponent;
import org.terasology.entitySystem.systems.In;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.RenderSystem;
import org.terasology.logic.characters.CharacterComponent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.math.AABB;
import org.terasology.math.TeraMath;
import org.terasology.network.ClientComponent;
import org.terasology.network.NetworkSystem;
import org.terasology.rendering.assets.material.Material;
import org.terasology.rendering.assets.mesh.Mesh;
import org.terasology.rendering.opengl.GLSLMaterial;
import org.terasology.rendering.opengl.OpenGLMesh;
import org.terasology.rendering.primitives.Tessellator;
import org.terasology.rendering.primitives.TessellatorHelper;
import org.terasology.rendering.world.WorldRenderer;

import javax.vecmath.AxisAngle4f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glRotatef;
import static org.lwjgl.opengl.GL11.glScalef;
import static org.lwjgl.opengl.GL11.glTranslated;

/**
 * TODO: This should be made generic (no explicit shader or mesh) and ported directly into WorldRenderer? Later note: some GelCube functionality moved to a mod
 *
 * @author Immortius <immortius@gmail.com>
 */
@RegisterSystem(RegisterMode.CLIENT)
public class MeshRenderer implements RenderSystem {
    private static final Logger logger = LoggerFactory.getLogger(MeshRenderer.class);

    @In
    private NetworkSystem network;

    @In
    private LocalPlayer localPlayer;

    private Mesh gelatinousCubeMesh;
    private WorldRenderer worldRenderer;

    private SetMultimap<Material, EntityRef> opaqueMesh = HashMultimap.create();
    private SetMultimap<Material, EntityRef> translucentMesh = HashMultimap.create();
    private Set<EntityRef> gelatinous = Sets.newHashSet();
    private Map<EntityRef, Material> opaqueEntities = Maps.newHashMap();
    private Map<EntityRef, Material> translucentEntities = Maps.newHashMap();

    public int lastRendered;

    @Override
    public void initialise() {
        worldRenderer = CoreRegistry.get(WorldRenderer.class);

        Tessellator tessellator = new Tessellator();
        TessellatorHelper.addBlockMesh(tessellator, new Vector4f(1.0f, 1.0f, 1.0f, 1.0f), 0.8f, 0.8f, 0.6f, 0f, 0f, 0f);
        TessellatorHelper.addBlockMesh(tessellator, new Vector4f(1.0f, 1.0f, 1.0f, 0.6f), 1.0f, 1.0f, 0.8f, 0f, 0f, 0f);
        gelatinousCubeMesh = tessellator.generateMesh();
    }

    @Override
    public void shutdown() {
    }

    @ReceiveEvent(components = {MeshComponent.class, LocationComponent.class})
    public void onNewMesh(OnActivatedComponent event, EntityRef entity) {
        addMesh(entity);
    }

    private void addMesh(EntityRef entity) {
        MeshComponent meshComp = entity.getComponent(MeshComponent.class);
        // Don't render if hidden from owner (need to improve for third person)
        if (meshComp.hideFromOwner) {
            ClientComponent owner = network.getOwnerEntity(entity).getComponent(ClientComponent.class);
            if (owner != null && owner.local) {
                return;
            }
        }
        if (meshComp.renderType == MeshComponent.RenderType.GelatinousCube) {
            gelatinous.add(entity);
        } else if (meshComp.material != null) {
            opaqueMesh.put(meshComp.material, entity);
            opaqueEntities.put(entity, meshComp.material);
        }
    }

    @ReceiveEvent(components = {CharacterComponent.class, MeshComponent.class})
    public void onLocalMesh(OnChangedComponent event, EntityRef entity) {
        removeMesh(entity);
        addMesh(entity);
    }

    @ReceiveEvent(components = {MeshComponent.class})
    public void onChangeMesh(OnChangedComponent event, EntityRef entity) {
        removeMesh(entity);
        addMesh(entity);
    }

    private void removeMesh(EntityRef entity) {
        if (!gelatinous.remove(entity)) {
            Material mat = opaqueEntities.remove(entity);
            if (mat != null) {
                opaqueMesh.remove(mat, entity);
            } else {
                mat = translucentEntities.remove(entity);
                if (mat != null) {
                    translucentMesh.remove(mat, entity);
                }
            }
        }
    }

    @ReceiveEvent(components = {MeshComponent.class, LocationComponent.class})
    public void onDestroyMesh(BeforeDeactivateComponent event, EntityRef entity) {
        removeMesh(entity);
    }

    @Override
    public void renderAlphaBlend() {

        Vector3f cameraPosition = worldRenderer.getActiveCamera().getPosition();
        GLSLMaterial material = (GLSLMaterial) Assets.getMaterial("engine:gelatinousCube");
        material.enable();

        for (EntityRef entity : gelatinous) {
            MeshComponent meshComp = entity.getComponent(MeshComponent.class);
            LocationComponent location = entity.getComponent(LocationComponent.class);
            if (location == null) {
                continue;
            }

            Quat4f worldRot = location.getWorldRotation();
            Vector3f worldPos = location.getWorldPosition();
            float worldScale = location.getWorldScale();
            AABB aabb = gelatinousCubeMesh.getAABB().transform(worldRot, worldPos, worldScale);
            if (worldRenderer.isAABBVisible(aabb)) {
                glPushMatrix();

                glTranslated(worldPos.x - cameraPosition.x, worldPos.y - cameraPosition.y, worldPos.z - cameraPosition.z);
                AxisAngle4f rot = new AxisAngle4f();
                rot.set(worldRot);
                glRotatef(TeraMath.RAD_TO_DEG * rot.angle, rot.x, rot.y, rot.z);
                glScalef(worldScale, worldScale, worldScale);

                material.setFloat4("colorOffset", meshComp.color.x, meshComp.color.y, meshComp.color.z, meshComp.color.w, true);
                material.setFloat("light", worldRenderer.getRenderingLightValueAt(worldPos), true);

                gelatinousCubeMesh.render();

                glPopMatrix();
            }
        }
    }

    @Override
    public void renderOpaque() {
        Vector3f cameraPosition = worldRenderer.getActiveCamera().getPosition();

        Quat4f worldRot = new Quat4f();
        Vector3f worldPos = new Vector3f();
        Matrix4f matrixWorldSpace = new Matrix4f();
        Transform transWorldSpace = new Transform();
        Matrix4f matrixCameraSpace = new Matrix4f();

        FloatBuffer tempMatrixBuffer44 = BufferUtils.createFloatBuffer(16);
        FloatBuffer tempMatrixBuffer33 = BufferUtils.createFloatBuffer(12);

        for (Material material : opaqueMesh.keys()) {
            OpenGLMesh lastMesh = null;
            material.enable();
            material.setFloat("sunlight", 1.0f);
            material.setFloat("blockLight", 1.0f);
            material.setMatrix4("projectionMatrix", worldRenderer.getActiveCamera().getProjectionMatrix());
            material.bindTextures();
            lastRendered = opaqueMesh.get(material).size();

            for (EntityRef entity : opaqueMesh.get(material)) {
                MeshComponent meshComp = entity.getComponent(MeshComponent.class);
                LocationComponent location = entity.getComponent(LocationComponent.class);

                if (location == null || meshComp.mesh == null) {
                    continue;
                }
                if (meshComp.mesh.isDisposed()) {
                    logger.error("Attempted to render disposed mesh");
                    continue;
                }

                location.getWorldRotation(worldRot);
                location.getWorldPosition(worldPos);
                float worldScale = location.getWorldScale();

                matrixWorldSpace.set(worldRot, worldPos, worldScale);
                transWorldSpace.set(matrixWorldSpace);

                Vector3f worldPositionCameraSpace = new Vector3f();
                worldPositionCameraSpace.sub(worldPos, cameraPosition);
                matrixCameraSpace.set(worldRot, worldPositionCameraSpace, worldScale);

                AABB aabb = meshComp.mesh.getAABB().transform(transWorldSpace);
                boolean visible = worldRenderer.isAABBVisible(aabb);
                if (visible) {
                    if (worldRenderer.isAABBVisible(aabb)) {
                        if (meshComp.mesh != lastMesh) {
                            if (lastMesh != null) {
                                lastMesh.postRender();
                            }
                            lastMesh = (OpenGLMesh) meshComp.mesh;
                            lastMesh.preRender();
                        }
                        Matrix4f modelViewMatrix = TeraMath.calcModelViewMatrix(worldRenderer.getActiveCamera().getViewMatrix(), matrixCameraSpace);
                        TeraMath.matrixToFloatBuffer(modelViewMatrix, tempMatrixBuffer44);

                        material.setMatrix4("worldViewMatrix", tempMatrixBuffer44, true);

                        TeraMath.matrixToFloatBuffer(TeraMath.calcNormalMatrix(modelViewMatrix), tempMatrixBuffer33);
                        material.setMatrix3("normalMatrix", tempMatrixBuffer33, true);

                        material.setFloat("sunlight", worldRenderer.getSunlightValueAt(worldPos), true);
                        material.setFloat("blockLight", worldRenderer.getBlockLightValueAt(worldPos), true);

                        lastMesh.doRender();
                    }
                }
            }
            if (lastMesh != null) {
                lastMesh.postRender();
            }
        }

        glPopMatrix();
    }

    @Override
    public void renderOverlay() {
    }

    @Override
    public void renderFirstPerson() {
    }

    @Override
    public void renderShadows() {
    }
}
