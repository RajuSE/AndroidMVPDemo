package com.mvp.moviedbapi

import android.support.test.espresso.Espresso.onView
import android.support.test.espresso.action.ViewActions.*
import android.support.test.espresso.assertion.ViewAssertions.matches
import android.support.test.espresso.matcher.RootMatchers.withDecorView
import android.support.test.espresso.matcher.ViewMatchers.*
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.support.v7.widget.RecyclerView
import android.view.View
import com.mvp.moviedbapi.activities.MainActivity
import com.mvp.moviedbapi.base.AbstractTest
import com.mvp.moviedbapi.base.Condition
import com.mvp.moviedbapi.constants.Urls
import com.mvp.moviedbapi.interfaces.MainActivityContract
import com.mvp.moviedbapi.models.apis.MovieResult
import com.mvp.moviedbapi.models.apis.SearchResults
import com.mvp.moviedbapi.models.managers.HttpManager
import com.mvp.moviedbapi.network.MovieSearchService
import com.mvp.moviedbapi.presenters.MainActivityPresenter
import com.nhaarman.mockito_kotlin.*
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNotSame
import org.hamcrest.Matchers.`is`
import org.hamcrest.core.IsNot.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import rx.Observable
import java.io.IOException
import java.util.*

/**
 * Instrumentation test, which will execute on an Android device.

 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest : AbstractTest() {

    @Rule @JvmField
    val mActivityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    @Throws(InterruptedException::class)
    fun testMainActivityMovieSearch() {
        // No text
        onView(withId(R.id.searchButton)).perform(click())
        onView(withText(R.string.search_error_no_text)).inRoot(withDecorView(not<View>(`is`<View>(mActivityRule.activity.window.decorView))))
                .check(matches(isDisplayed()))

        // Mock Type text and then press the button.
        onView(withId(R.id.edittext))
                .perform(typeText("star wars"), closeSoftKeyboard())
        onView(withId(R.id.searchButton)).perform(click())

        // Check that list adapter is set and views populated
        val recyclerView = mActivityRule.activity.findViewById(R.id.recyclerView) as RecyclerView
        //One improvement would be not to rely on the real network query, but mock the response (Mockito etc...) to avoid depending on network related stuff.
        waitForCondition(object : Condition {
            override val isSatisfied: Boolean
                get() = recyclerView.adapter != null
        }, 3000)//Mock Check Adapter!=null After 3 Seconds Means Response is received!
        assertNotNull(recyclerView)
        assertNotNull(recyclerView.adapter)
        assertNotSame(0, recyclerView.adapter.itemCount)
    }

    //TODO test for next page (did not have the time)


    val SEARCH = "star wars"

    @Test
    @Throws(InterruptedException::class)
    fun testSearchMovie() {
        val mainActivityPresenter = MainActivityPresenter()
        val mainActivityView = mock<MainActivityContract.MainActivityView>()

        //Test null view is not crashing at least
        mainActivityPresenter.searchMovie("", 1)

        //Test null text
        mainActivityPresenter.attach(mainActivityView)
        mainActivityPresenter.searchMovie("", 1)
        verify(mainActivityView).showToast(R.string.search_error_no_text)


        //Test ok response
        val searchResults = fakeSearchResults
        var movieSearchService = mock<MovieSearchService> {
            on { getMovies(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()) } doReturn Observable.just(searchResults)
        }
        HttpManager.instance.movieSearchService = movieSearchService//Initialize with mocked Service
        mainActivityPresenter.searchMovie(SEARCH, 1)//Now Call the method
        waitFor(100)

        //Verify movieSearchService called with right params
        verify(movieSearchService, atLeastOnce()).getMovies(eq(Urls.MOVIEDB_API_KEY_VALUE), eq(SEARCH), eq(1))

        //Verify updateMovieAdapter with response
        verify(mainActivityView, atLeastOnce()).updateMovieAdapter(eq(searchResults))

        //Verify next button
        verify(mainActivityView, atLeastOnce()).setUpOnNextPageButton(eq(SEARCH), eq(View.VISIBLE), eq(2))
    }

    @Test
    fun testErrorResponse() {
        val mainActivityPresenter = MainActivityPresenter()
        val mainActivityView = mock<MainActivityContract.MainActivityView>()

        //Test error response
        var movieSearchService = mock<MovieSearchService> {
            on { getMovies(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(), ArgumentMatchers.anyInt()) } doReturn Observable.error<SearchResults>(IOException())
        }
        HttpManager.instance.movieSearchService = movieSearchService
        mainActivityPresenter.searchMovie(SEARCH, 1)
        waitFor(50)
        verify(mainActivityView, atLeastOnce()).showToast(R.string.search_error_text)
    }

    /**
     * Returns a fake initialiazed [SearchResults]
     */
    private val fakeSearchResults: SearchResults
        get() {
            val searchResults = SearchResults()
            searchResults.page = 1
            searchResults.totalPages = 2
            val movieResults = Arrays.asList(MovieResult(), MovieResult())
            searchResults.results = movieResults
            return searchResults
        }
}
