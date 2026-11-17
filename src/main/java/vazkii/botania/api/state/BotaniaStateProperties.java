package vazkii.botania.api.state;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Holds all Botania block state properties. Use these to set botania blockstates
 */
public final class BotaniaStateProperties {

    public enum OptionalDyeColor implements StringRepresentable {

        WHITE,
        ORANGE,
        MAGENTA,
        LIGHT_BLUE,
        YELLOW,
        LIME,
        PINK,
        GRAY,
        LIGHT_GRAY,
        CYAN,
        PURPLE,
        BLUE,
        BROWN,
        GREEN,
        RED,
        BLACK,
        NONE;

        public Optional<DyeColor> toDyeColor() {
            return switch (this) {
                case WHITE -> Optional.of(DyeColor.WHITE);
                case ORANGE -> Optional.of(DyeColor.ORANGE);
                case MAGENTA -> Optional.of(DyeColor.MAGENTA);
                case LIGHT_BLUE -> Optional.of(DyeColor.LIGHT_BLUE);
                case YELLOW -> Optional.of(DyeColor.YELLOW);
                case LIME -> Optional.of(DyeColor.LIME);
                case PINK -> Optional.of(DyeColor.PINK);
                case GRAY -> Optional.of(DyeColor.GRAY);
                case LIGHT_GRAY -> Optional.of(DyeColor.LIGHT_GRAY);
                case CYAN -> Optional.of(DyeColor.CYAN);
                case PURPLE -> Optional.of(DyeColor.PURPLE);
                case BLUE -> Optional.of(DyeColor.BLUE);
                case BROWN -> Optional.of(DyeColor.BROWN);
                case GREEN -> Optional.of(DyeColor.GREEN);
                case RED -> Optional.of(DyeColor.RED);
                case BLACK -> Optional.of(DyeColor.BLACK);
                case NONE -> Optional.empty();
            };
        }

        @Override
        public @NotNull String getSerializedName() {
            return this.toDyeColor().map(DyeColor::getSerializedName).orElse("none");
        }
    }

    // Currently only ManaPools.PoolBlock
    public static final EnumProperty<OptionalDyeColor> OPTIONAL_DYE_COLOR = EnumProperty.create("color", OptionalDyeColor.class);

    private BotaniaStateProperties() {}
}
