package dev.plattnericus.cases.inspect;

import java.util.List;

public record KnifeModel(String id, String animation, List<ModelPart> parts) {

    public KnifeModel {
        parts = List.copyOf(parts);
    }
}
