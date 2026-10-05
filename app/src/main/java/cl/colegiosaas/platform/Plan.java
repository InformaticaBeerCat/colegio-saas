package cl.colegiosaas.platform;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static cl.colegiosaas.platform.Feature.*;

/** Planes comerciales (sección 7 de requerimientos). Cada plan incluye al anterior. */
public enum Plan {
    BASE(EnumSet.of(NEWS, CALENDAR, GALLERIES, CONTACT, SAE_ADMISSIONS)),
    COMMUNITY(extend(BASE, SCHEDULING, EVENTS, NEWSLETTER, PUSH_NOTIFICATIONS, COMMUNITY_AREA)),
    ADMISSIONS_PRO(extend(COMMUNITY, OWN_ADMISSIONS, PROSPECT_CRM, AUTOMATED_FOLLOW_UP));

    private final Set<Feature> includedFeatures;

    Plan(EnumSet<Feature> features) {
        this.includedFeatures = Collections.unmodifiableSet(features);
    }

    public Set<Feature> includedFeatures() {
        return includedFeatures;
    }

    private static EnumSet<Feature> extend(Plan base, Feature... extra) {
        EnumSet<Feature> features = EnumSet.copyOf(base.includedFeatures);
        Collections.addAll(features, extra);
        return features;
    }
}
