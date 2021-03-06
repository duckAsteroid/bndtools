package bndtools.model.obr;

import java.util.Comparator;

import org.apache.felix.bundlerepository.Requirement;

public class RequirementComparator implements Comparator<Requirement> {

    public int compare(Requirement req1, Requirement req2) {
        int diff;
        
        diff = ComparatorUtils.safeCompare(req1.getName(), req2.getName());
        if (diff != 0)
            return diff;

        diff = ComparatorUtils.safeCompare(req1.getFilter(), req2.getFilter());
        if (diff != 0)
            return diff;

        diff = ComparatorUtils.safeCompare(req1.getComment(), req2.getComment());

        return diff;
    }

}
