package github.leavesc.monitor.ui

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import github.leavesc.monitor.R
import github.leavesc.monitor.viewmodel.MonitorDetailViewModel
import kotlinx.android.synthetic.main.fragment_monitor_request.*

/**
 * 作者：leavesC
 * 时间：2020/11/8 10:18
 * 描述：
 * GitHub：https://github.com/leavesC
 */
class MonitorRequestFragment : Fragment() {

    companion object {

        fun newInstance(): MonitorRequestFragment {
            return MonitorRequestFragment()
        }

    }

    private val monitorDetailViewModel by lazy {
        ViewModelProvider(activity!!).get(MonitorDetailViewModel::class.java).apply {
            recordLiveData.observe(this@MonitorRequestFragment, Observer { httpInformation ->
                if (httpInformation != null) {
                    val headersString = httpInformation.getRequestHeadersString(true)
                    if (headersString.isBlank()) {
                        tvHeaders.visibility = View.GONE
                    } else {
                        tvHeaders.visibility = View.VISIBLE
                        tvHeaders.text = Html.fromHtml(headersString)
                    }
                    tvBody.text = httpInformation.requestBodyFormat
                }
            })
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_monitor_request, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        monitorDetailViewModel.init()
    }

}