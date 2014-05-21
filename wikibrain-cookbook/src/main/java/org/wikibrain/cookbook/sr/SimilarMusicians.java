package org.wikibrain.cookbook.sr;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.core.model.NameSpace;
import org.wikibrain.sr.MonolingualSRMetric;
import org.wikibrain.sr.SRResult;
import org.wikibrain.wikidata.WikidataDao;
import org.wikibrain.wikidata.WikidataStatement;
import org.wikibrain.wikidata.WikidataValue;

import java.util.List;
import java.util.Set;

/**
 * @author Shilad Sen
 */
public class SimilarMusicians {
    public static void main(String args[]) throws ConfigurationException, DaoException {
        Env env = EnvBuilder.envFromArgs(args);
        Language lang = env.getLanguages().getDefaultLanguage();
        WikidataDao wdd = env.getConfigurator().get(WikidataDao.class);
        LocalPageDao lpd = env.getConfigurator().get(LocalPageDao.class);
        MonolingualSRMetric sr = env.getConfigurator().get(MonolingualSRMetric.class, "ensemble", "language", lang.getLangCode());

        TIntSet musicians = new TIntHashSet();
//        for (LocalId lid : wdd.pagesWithValue("occupation", WikidataValue.forItem(639669), Language.SIMPLE)) {
        for (LocalId lid : wdd.pagesWithValue("member of political party", WikidataValue.forItem(29552), lang)) {
            musicians.add(lid.getId());
        }

        int milesId = lpd.getIdByTitle("Miles Davis", lang, NameSpace.ARTICLE);
        for (SRResult hit : sr.mostSimilar(milesId, 5, musicians)) {
            System.out.println(lpd.getById(lang, hit.getId()));
        }
    }
}
