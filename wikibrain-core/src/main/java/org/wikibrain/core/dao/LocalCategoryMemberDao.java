package org.wikibrain.core.dao;

import org.wikibrain.core.WikiBrainException;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.CategoryGraph;
import org.wikibrain.core.model.LocalCategoryMember;
import org.wikibrain.core.model.LocalPage;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 *
 * An interface that describes a Dao to determine local category membership.
 *
 * @author Ari Weiland
 *
 */
public interface LocalCategoryMemberDao extends Dao<LocalCategoryMember> {

    /**
     * Supplemental method that saves a membership relationship based on
     * a LocalCategory and LocalArticle
     * @param category a LocalCategory
     * @param article a LocalArticle that is a member of the LocalCategory
     * @throws DaoException if there was an error saving the item
     * @throws org.wikibrain.core.WikiBrainException if the category and article are in different languages
     */
    public void save(LocalPage category, LocalPage article) throws DaoException, WikiBrainException;

    public LocalPage getClosestCategory(LocalPage page, Set<LocalPage> candidates, boolean weightedDistance) throws DaoException;

    /**
     * Gets a collection of page IDs of articles that are members of the category
     * specified by the language and category ID
     * @param language the language of the category
     * @param categoryId the category's ID
     * @return a collection of page IDs of articles
     * @throws DaoException if there was an error retrieving the pages
     */
    public Collection<Integer> getCategoryMemberIds(Language language, int categoryId) throws DaoException;

    /**
     * Gets a collection of page IDs of articles that are members of the category
     * @param localCategory the category
     * @return a collection of page IDs of articles
     * @throws DaoException if there was an error retrieving the pages
     */
    public Collection<Integer> getCategoryMemberIds(LocalPage localCategory) throws DaoException;

    /**
     * Gets a map of local articles mapped from their page IDs, based on a category
     * specified by a language and category ID
     * @param language the language of the category
     * @param categoryId the category's ID
     * @return a map of page IDs to articles
     * @throws DaoException if there was an error retrieving the pages
     */
    public Map<Integer, LocalPage> getCategoryMembers(Language language, int categoryId) throws DaoException;

    /**
     * Gets a map of local articles mapped from their page IDs, based on a specified category
     * @param localCategory the category to find
     * @return a map of page IDs to articles
     * @throws DaoException if there was an error retrieving the pages
     */
    public Map<Integer, LocalPage> getCategoryMembers(LocalPage localCategory) throws DaoException;

    /**
     * Gets a collection of page IDs of categories that the article specified by
     * the language and category ID is a member of
     * @param language the language of the article
     * @param articleId the articles's ID
     * @return a collection of page IDs of categories
     * @throws DaoException if there was an error retrieving the pages
     */
    public Collection<Integer> getCategoryIds(Language language, int articleId) throws DaoException;

    /**
     * Gets a collection of page IDs of categories that the article is a member of
     * @param localArticle the article
     * @return a collection of page IDs of categories
     * @throws DaoException if there was an error retrieving the pages
     */
    public Collection<Integer> getCategoryIds(LocalPage localArticle) throws DaoException;

    /**
     * Gets a map of local categories mapped from their page IDs, based on an article
     * specified by a language and article ID
     * @param language the language of the article
     * @param articleId the article's ID
     * @return a map of page IDs to categories
     * @throws DaoException if there was an error retrieving the pages
     */
    public Map<Integer, LocalPage> getCategories(Language language, int articleId) throws DaoException;

    /**
     * Gets a map of local categories mapped from their page IDs, based on a specified article
     * @param localArticle the article to find
     * @return a map of page IDs to categories
     * @throws DaoException if there was an error retrieving the pages
     */
    public Map<Integer, LocalPage> getCategories(LocalPage localArticle) throws DaoException;

    /**
     * Returns a compact representation of the category graph.
     * The return value of this object is shared and cached, so caller must not change it.
     * TODO: make CategoryGraph immutable.
     * @param language
     * @return
     */
    public CategoryGraph getGraph(Language language) throws DaoException;

}
