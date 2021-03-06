/*******************************************************************************
 * HellFirePvP / Modular Machinery 2018
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 ******************************************************************************/

package hellfirepvp.modularmachinery.common.crafting.helper;

import com.google.common.collect.Lists;
import hellfirepvp.modularmachinery.common.crafting.ComponentType;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.requirements.RequirementEnergy;
import hellfirepvp.modularmachinery.common.machine.MachineComponent;
import hellfirepvp.modularmachinery.common.modifier.ModifierReplacement;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.util.ResultChance;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is part of the Modular Machinery Mod
 * The complete source code for this mod can be found on github.
 * Class: RecipeCraftingContext
 * Created by HellFirePvP
 * Date: 28.06.2017 / 12:23
 */
public class RecipeCraftingContext {

    private static final Random RAND = new Random();

    private final MachineRecipe recipe;
    private int currentCraftingTick = 0;
    private Map<String, Map<MachineComponent, Object>> typeComponents = new HashMap<>();
    private Map<String, List<RecipeModifier>> modifiers = new HashMap<>();

    private List<ComponentOutputRestrictor> currentRestrictions = Lists.newArrayList();

    public RecipeCraftingContext(MachineRecipe recipe) {
        this.recipe = recipe;
    }

    public MachineRecipe getParentRecipe() {
        return recipe;
    }

    public void setCurrentCraftingTick(int currentCraftingTick) {
        this.currentCraftingTick = currentCraftingTick;
    }

    public int getCurrentCraftingTick() {
        return currentCraftingTick;
    }

    @Nonnull
    public List<RecipeModifier> getModifiers(String target) {
        return modifiers.computeIfAbsent(target, t -> new LinkedList<>());
    }

    public float applyModifiers(ComponentRequirement reqTarget, MachineComponent.IOType ioType, float value, boolean isChance) {
        return applyModifiers(reqTarget.getRequiredComponentType().getRegistryName(), ioType, value, isChance);
    }

    public float applyModifiers(String target, MachineComponent.IOType ioType, float value, boolean isChance) {
        List<RecipeModifier> applicable = getModifiers(target);
        applicable = applicable.stream().filter(mod -> (ioType == null || mod.getIOTarget() == ioType) && mod.affectsChance() == isChance).collect(Collectors.toList());
        float add = 0F;
        float mul = 1F;
        for (RecipeModifier mod : applicable) {
            if(mod.getOperation() == 0) {
                add += mod.getModifier();
            } else if(mod.getOperation() == 1) {
                mul *= mod.getModifier();
            } else {
                throw new RuntimeException("Unknown modifier operation: " + mod.getOperation() + " at recipe " + recipe.getRegistryName());
            }
        }
        return (value + add) * mul;
    }

    public float getDurationMultiplier() {
        float dur = this.recipe.getRecipeTotalTickTime();
        float result = applyModifiers("duration", null, dur, false);
        return dur / result;
    }

    public void addRestriction(ComponentOutputRestrictor restrictor) {
        this.currentRestrictions.add(restrictor);
    }

    public Collection<MachineComponent> getComponentsFor(ComponentType type) {
        String key = type.getRegistryName();
        if(key.equalsIgnoreCase("gas")) {
            key = "fluid";
        }
        return this.typeComponents.computeIfAbsent(key, (s) -> new HashMap<>()).keySet();
    }

    public boolean energyTick() {
        float durMultiplier = this.getDurationMultiplier();
        for (ComponentRequirement requirement : this.recipe.getCraftingRequirements()) {
            if(!(requirement instanceof ComponentRequirement.PerTick) ||
                    requirement.getActionType() == MachineComponent.IOType.OUTPUT) continue;
            ComponentRequirement.PerTick perTickRequirement = (ComponentRequirement.PerTick) requirement;

            perTickRequirement.resetIOTick(this);
            perTickRequirement.startIOTick(this, durMultiplier);
            boolean enough = false;
            lblComps:
            for (MachineComponent component : getComponentsFor(requirement.getRequiredComponentType())) {
                ComponentRequirement.CraftCheck result = perTickRequirement.doIOTick(component, this);
                switch (result) {
                    case SUCCESS:
                        enough = true;
                        break lblComps;
                }
            }
            perTickRequirement.resetIOTick(this);
            if(!enough) {
                return false;
            }
        }

        for (ComponentRequirement requirement : this.recipe.getCraftingRequirements()) {
            if(!(requirement instanceof ComponentRequirement.PerTick) ||
                    requirement.getActionType() == MachineComponent.IOType.INPUT) continue;
            ComponentRequirement.PerTick perTickRequirement = (ComponentRequirement.PerTick) requirement;

            perTickRequirement.resetIOTick(this);
            perTickRequirement.startIOTick(this, durMultiplier);
            lblComps:
            for (MachineComponent component : getComponentsFor(requirement.getRequiredComponentType())) {
                ComponentRequirement.CraftCheck result = perTickRequirement.doIOTick(component, this);
                switch (result) {
                    case SUCCESS:
                        break lblComps;
                    case PARTIAL_SUCCESS:
                        break;
                    case FAILURE_MISSING_INPUT:
                        break;
                    case INVALID_SKIP:
                        break;
                }
            }
            perTickRequirement.resetIOTick(this);

        }
        return true;
    }

    public void startCrafting() {
        startCrafting(RAND.nextLong());
    }

    public void startCrafting(long seed) {
        ResultChance chance = new ResultChance(seed);
        for (ComponentRequirement requirement : this.recipe.getCraftingRequirements()) {
            if(requirement.getActionType() == MachineComponent.IOType.OUTPUT) continue;

            requirement.startRequirementCheck(chance, this);
            for (MachineComponent component : getComponentsFor(requirement.getRequiredComponentType())) {
                if(requirement.startCrafting(component, this, chance)) {
                    requirement.endRequirementCheck();
                    break;
                }
            }
            requirement.endRequirementCheck();
        }
    }

    public void finishCrafting() {
        finishCrafting(RAND.nextLong());
    }

    public void finishCrafting(long seed) {
        ResultChance chance = new ResultChance(seed);
        for (ComponentRequirement requirement : this.recipe.getCraftingRequirements()) {
            if(requirement.getActionType() == MachineComponent.IOType.INPUT) continue;

            requirement.startRequirementCheck(chance, this);
            for (MachineComponent component : getComponentsFor(requirement.getRequiredComponentType())) {
                if(requirement.finishCrafting(component, this, chance)) {
                    requirement.endRequirementCheck();
                    break;
                }
            }
            requirement.endRequirementCheck();
        }
    }

    public ComponentRequirement.CraftCheck canStartCrafting() {
        currentRestrictions.clear();

        lblRequirements:
        for (ComponentRequirement requirement : recipe.getCraftingRequirements()) {
            if(requirement.getRequiredComponentType().equals(ComponentType.Registry.getComponent("energy")) &&
                    requirement.getActionType() == MachineComponent.IOType.OUTPUT) {

                for (MachineComponent component : getComponentsFor(requirement.getRequiredComponentType())) {
                    if(component.getIOType() == MachineComponent.IOType.OUTPUT) {
                        continue lblRequirements; //Check if it has at least 1 energy output.
                    }
                }
                return ComponentRequirement.CraftCheck.FAILURE_MISSING_INPUT;
            }

            requirement.startRequirementCheck(ResultChance.GUARANTEED, this);

            for (MachineComponent component : getComponentsFor(requirement.getRequiredComponentType())) {
                ComponentRequirement.CraftCheck check = requirement.canStartCrafting(component, this, this.currentRestrictions);
                if(check == ComponentRequirement.CraftCheck.SUCCESS) {
                    requirement.endRequirementCheck();
                    continue lblRequirements;
                }
            }

            requirement.endRequirementCheck();
            currentRestrictions.clear();
            return ComponentRequirement.CraftCheck.FAILURE_MISSING_INPUT;
        }
        currentRestrictions.clear();
        return ComponentRequirement.CraftCheck.SUCCESS;
    }

    public void addComponent(MachineComponent<?> component) {
        Map<MachineComponent, Object> components = this.typeComponents.computeIfAbsent(component.getComponentType().getRegistryName(), (s) -> new HashMap<>());
        components.put(component, component.getContainerProvider());
    }

    public void addModifier(ModifierReplacement modifier) {
        RecipeModifier mod = modifier.getModifier();
        this.modifiers.computeIfAbsent(mod.getTarget(), target -> new LinkedList<>()).add(mod);
    }

    @Nullable
    public Object getProvidedCraftingComponent(MachineComponent component) {
        Map<MachineComponent, Object> components = this.typeComponents.computeIfAbsent(component.getComponentType().getRegistryName(), (s) -> new HashMap<>());
        return components.getOrDefault(component, null);
    }

}
