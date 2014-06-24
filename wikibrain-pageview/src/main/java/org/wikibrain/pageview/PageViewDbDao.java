package org.wikibrain.pageview;

import com.typesafe.config.Config;
import gnu.trove.procedure.TIntIntProcedure;
import org.joda.time.DateTime;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.wikibrain.conf.Configuration;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.WikiBrainException;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalLinkDao;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LanguageSet;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Toby "Jiajun" Li
 */
public class PageViewDbDao {

    DB db;
    Language lang;
    Set<Long> parsedHourSet;
    PageViewDbDao(Language lang){
        this.lang = lang;
        //TODO: Find a new way to deal with the path issue...It is probably not a great idea to hard code the path
        this.db = DBMaker.newFileDB(new File("./db/" + lang.getLangCode() + "_page_view_db")).closeOnJvmShutdown().make();
        if(db.exists("parsedHourSet")) {
            this.parsedHourSet = db.getTreeSet("parsedHourSet");
        }
        else
            this.parsedHourSet = db.createTreeSet("parsedHourSet").make();
    }

    /**
     * method to access a PageViewIterator via the DAO, can be used by clients to keep track of each of the PageViewDataStructs
     * retrieved by the iterator
     * @param langs
     * @param startDate
     * @param endDate
     * @return
     * @throws WikiBrainException
     * @throws DaoException
     */
    public PageViewIterator getPageViewIterator(LanguageSet langs, DateTime startDate, DateTime endDate, LocalPageDao localPageDao) throws WikiBrainException, DaoException {
        return new PageViewIterator(langs, startDate, endDate, localPageDao);
    }


    /**
     *  Adds a PageViewDataStruct record to database
     * @param data The PageViewDataStruct being added
     */
    public void addData(PageViewDataStruct data){
        final Long dateId =  data.getStartDate().getMillis();

        data.getPageViewStats().forEachEntry(new TIntIntProcedure() {
            @Override
            public boolean execute(int a, int b) {
                if (b == 0)
                    return true;
                else if (db.exists(Integer.toString(a))) {
                    Map<Long, Integer> hourViewMap = db.getTreeMap(Integer.toString(a));
                    hourViewMap.put(dateId, b);
                    return true;
                } else {
                    Map<Long, Integer> hourViewMap = db.createTreeMap(Integer.toString(a)).make();
                    hourViewMap.put(dateId, b);
                    return true;
                }
            }

        });
        System.out.println(dateId);
        parsedHourSet.add(dateId);
        db.commit();
    }

    /**
     * Get the number of page views for a id in an hour
     * @param id Page id
     * @param year The year we are getting page view in
     * @param month The month we are getting page view in
     * @param day The day we are getting page view in
     * @param hour The hour we are getting page view in
     * @return The number of page views
     */
    public int getPageView(int id, int year, int month, int day, int hour, LocalPageDao localPageDao)throws ConfigurationException, DaoException, WikiBrainException{
        DateTime time = new DateTime(year, month, day, hour, 0);
        if(!parsedHourSet.contains(time.getMillis())){
            parse(time, localPageDao);
        }
        if(db.exists(Integer.toString(id)) == false)
            return 0;
        Map<Long, Integer> hourViewMap = db.getTreeMap(Integer.toString(id));
        if(hourViewMap.containsKey(time.getMillis()) == false)
            return 0;
        else{

            return hourViewMap.get(time.getMillis());
        }

    }

    /**
     * Get the number of page views for a id in a given period
     * @param id Page id
     * @param startYear
     * @param startMonth
     * @param startDay
     * @param startHour
     * @param numHours Number of hours from the start date specified by the above parameters; defines the time period
     * @return The number of page views
     */

    //hourly
    public int getPageView(int id, int startYear, int startMonth, int startDay, int startHour, int numHours, LocalPageDao localPageDao) throws ConfigurationException, DaoException, WikiBrainException{
        int sum = 0;
        DateTime startTime = new DateTime(startYear, startMonth, startDay, startHour, 0);
        DateTime endTime = startTime.plusHours(numHours);
        if(!checkExist(startTime, endTime))
            parse(startTime, numHours, localPageDao);
        if(db.exists(Integer.toString(id)) == false)
            return 0;
        Map<Long, Integer> hourViewMap = db.getTreeMap(Integer.toString(id));
        for(DateTime hrTime = startTime; hrTime.getMillis() < endTime.getMillis(); hrTime = hrTime.plusHours(1)){
            if(hourViewMap.containsKey(hrTime.getMillis()) == false)
                continue;
            sum += hourViewMap.get(hrTime.getMillis());
        }

        return sum;

    }

    public Map<Integer, Integer> getPageView(Iterable<Integer> ids, int startYear, int startMonth, int startDay, int startHour,
        int numHours, LocalPageDao localPageDao) throws ConfigurationException, DaoException, WikiBrainException{
        Map<Integer, Integer> result = new HashMap<Integer, Integer>();
        DateTime startTime = new DateTime(startYear, startMonth, startDay, startHour, 0);
        DateTime endTime = startTime.plusHours(numHours);
        if(!checkExist(startTime, endTime))
            parse(startTime, numHours, localPageDao);
        for(Integer id: ids){
            if(db.exists(Integer.toString(id)) == false){
                result.put(id, 0);
                continue;
            }
            Map<Long, Integer> hourViewMap = db.getTreeMap(Integer.toString(id));
            int sum = 0;
            for(DateTime hrTime = startTime; hrTime.getMillis() < endTime.getMillis(); hrTime = hrTime.plusHours(1)){
                if(hourViewMap.containsKey(hrTime.getMillis()) == false)
                    continue;
                sum += hourViewMap.get(hrTime.getMillis());
            }
            result.put(id, sum);
        }
        return result;
    }

    /**
     * Util function created iterator to parse page view file from startTime through a given number of hours
     * @param startTime the specified start time
     * @param numHours  the specified number of hours from startTime for which to parse page view files
     * @throws ConfigurationException
     * @throws DaoException
     * @throws WikiBrainException
     */
    void parse(DateTime startTime, int numHours, LocalPageDao localPageDao)throws ConfigurationException, DaoException, WikiBrainException {
        DateTime endTime = startTime.plusHours(numHours);
        PageViewIterator it = new PageViewIterator(new LanguageSet(lang), startTime, endTime, localPageDao);
        List<PageViewDataStruct> data;
        while(it.hasNext()){
            data = it.next();
            for (PageViewDataStruct struct : data) {
                addData(struct);
            }
        }


    }

    /**
     * Util function created iterator to parse view file for a single hour
     * @param time the specified hour to parse
     * @throws ConfigurationException
     * @throws DaoException
     * @throws WikiBrainException
     */
    void parse(DateTime time, LocalPageDao localPageDao)throws ConfigurationException, DaoException, WikiBrainException {
        PageViewIterator it = new PageViewIterator(new LanguageSet(lang), time, localPageDao);
        List<PageViewDataStruct> data;
        while(it.hasNext()){
            data = it.next();
            for (PageViewDataStruct struct : data) {
                addData(struct);
            }
        }


    }

    /**
     *  Util function used to check if all hours in a given period have been parsed
     * @param startTime start time of a period
     * @param endTime end time of a period
     * @return
     */
    boolean checkExist(DateTime startTime, DateTime endTime){
        for(DateTime hrTime = startTime; hrTime.getMillis() < endTime.getMillis(); hrTime = hrTime.plusHours(1)){
            if(!parsedHourSet.contains(hrTime.getMillis())) {
                System.out.println("does exist");
                return false;
            }
        }
        return true;
    }

    public static class Provider extends org.wikibrain.conf.Provider<PageViewDbDao> {
        public Provider(Configurator configurator, Configuration config) throws ConfigurationException {
            super(configurator, config);
        }

        @Override
        public Class getType() {
            return PageViewDbDao.class;
        }

        @Override
        public String getPath() {
            return "dao.pageView";
        }

        @Override
        public PageViewDbDao get(String name, Config config, Map<String, String> runtimeParams) throws ConfigurationException {
            if (!config.getString("type").equals("db")) {
                return null;
            }
            //TODO: make PageViewDbDao language agnostic
            //Toby: That will make it much slower parsing the dump file...as we'll have to store the number of pageviews for pages of all languages
            return new PageViewDbDao(Language.getByLangCode("simple"));
        }
    }

}
