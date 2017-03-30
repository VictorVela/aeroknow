package biz.eventually.atpl.ui.subject

import android.util.Log
import biz.eventually.atpl.common.RxBaseManager
import biz.eventually.atpl.network.DataProvider
import biz.eventually.atpl.network.model.Subject
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by thibault on 28/03/17.
 */

@Singleton class SubjectManager @Inject constructor(private val dataProvider: DataProvider) : RxBaseManager() {

    companion object {
        val TAG = "SubjectManager"
    }

    fun getSubjects(sourceId: Int, display: (List<Subject>?) -> Unit) {
        dataProvider.dataGetSubjects(sourceId)?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())?.subscribe({ s ->
            display(s)
        }, { error ->
            Log.d(TAG, "getSubject")
        })
    }
}