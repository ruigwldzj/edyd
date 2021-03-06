package com.oto.edyd;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.oto.edyd.model.TrackBean;
import com.oto.edyd.utils.Common;
import com.oto.edyd.utils.Constant;
import com.oto.edyd.utils.CusProgressDialog;
import com.oto.edyd.utils.OkHttpClientManager;
import com.squareup.okhttp.Request;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by liubaozhong on 2015/11/30.
 */
public class TrackListActivity extends Activity {
    private ListView lv_track;
    private Context mActivity;
    private EditText et_input_ordernum;
    private String sessionUuid;
    private ArrayList<TrackBean> infos;
    private TrackListAdapter adapter;
    private CusProgressDialog loadingDialog; //页面切换过度
    private String aspectType;
    private SwipeRefreshLayout swipe_container;

    private static final int firstLoad = 0;//第一次加载
    private static final int refreshLoad = 1;//刷新加载
    private static final int moreLoad = 2;//下拉加载更多
    private static final int searchLoad = 3;//查询加载
    private int page = 1;//默认加载的页数
    private int rows = 20;//默认加载的条数

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.track_list_activity);
        //得到角色类型
        Intent intent = getIntent();
        aspectType = intent.getStringExtra("aspectType");

        initfield();
        initView();
        fillDate(firstLoad, "");
    }

    private void initView() {
        swipe_container = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        swipe_container.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            /**
             * 刷新要做的操作
             */
            @Override
            public void onRefresh() {
                fillDate(refreshLoad, "");
            }
        });

        et_input_ordernum = (EditText) findViewById(R.id.et_input_ordernum);
        et_input_ordernum.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                fillDate(searchLoad, s.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        lv_track = (ListView) findViewById(R.id.lv_track);
        lv_track.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Intent intent = new Intent(mActivity, ShowTrackActivity.class);
//                Intent intent = new Intent(mActivity, ShowTrackActivity3D.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable("detailBean", infos.get(i));
                intent.putExtras(bundle);
                startActivity(intent);
            }
        });

        lv_track.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                switch (scrollState) {
                    case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:
                        int lastPosition = lv_track.getLastVisiblePosition();
                        if (lastPosition == infos.size() - 1) {
                            page++;
                            fillDate(moreLoad, "");
                        }
                        break;
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {

            }
        });

    }

    private void initfield() {
        mActivity = this;
        Common common = new Common(getSharedPreferences(Constant.LOGIN_PREFERENCES_FILE, Context.MODE_PRIVATE));
        sessionUuid = common.getStringByKey(Constant.SESSION_UUID);
        infos = new ArrayList<TrackBean>();
    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0x12: //油卡金额数据返回执行
                    //加载完成隐藏loading
                    loadingDialog.getLoadingDialog().dismiss();
                    swipe_container.setRefreshing(false);

                    if (adapter == null) {
                        adapter = new TrackListAdapter();
                        lv_track.setAdapter(adapter);
                    } else {
                        adapter.notifyDataSetChanged();
                    }
                    break;
            }
        }
    };

    private void fillDate(final int loadType, String controlNum) {
        //第一次进来显示loading
        if (loadType == firstLoad) {
            loadingDialog = new CusProgressDialog(mActivity, "正在获取数据...");
            loadingDialog.getLoadingDialog().show();
        }
        //v1.1/appTraceAllOrder.json?sessionUuid=68455ead6e094b0584a25c8d5a83069f&aspectType=3
        String url = Constant.ENTRANCE_PREFIX_v1 + "appTraceAllOrder.json?sessionUuid="
                + sessionUuid + "&controlNum=" + controlNum + "&page=" + page + "&rows=" + rows
                + "&aspectType=" + aspectType;
//        Common.printErrLog("轨迹" + url);
        OkHttpClientManager.getAsyn(url, new OkHttpClientManager.ResultCallback<String>() {
            @Override
            public void onError(Request request, Exception e) {
                loadingDialog.getLoadingDialog().dismiss();
                swipe_container.setRefreshing(false);
                Common.showToast(mActivity, "获取信息异常");
            }

            @Override
            public void onResponse(String response) {
//                Common.printErrLog("轨迹" + response);
                JSONObject jsonObject;
                JSONArray jsonArray;
                try {
                    jsonObject = new JSONObject(response);
                    if (!jsonObject.getString("status").equals(Constant.LOGIN_SUCCESS_STATUS)) {
                        Toast.makeText(getApplicationContext(), "返回信息失败", Toast.LENGTH_SHORT).show();
                        loadingDialog.getLoadingDialog().dismiss();
                        swipe_container.setRefreshing(false);
                        return;
                    }
                    jsonArray = jsonObject.getJSONArray("rows");

                    requestDistributeUserList(jsonArray, loadType);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void requestDistributeUserList(JSONArray jsonArray, int loadType) throws JSONException {

        ArrayList<TrackBean> tempList = new ArrayList<TrackBean>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            TrackBean bean = new TrackBean();
            bean.setPrimaryId(obj.getLong("primaryId"));
            bean.setControlNum(obj.getString("controlNum"));
            bean.setTruckNum(obj.getString("truckNum"));
            bean.setReserveNum(obj.getString("reserveNum"));
            bean.setOrderDate(obj.getString("orderDate"));
            bean.setControlStatus(obj.getInt("controlStatus"));
            tempList.add(bean);
        }

        switch (loadType) {
            case firstLoad:
                //是第一次加载数据
                if (tempList.size() == 0) {
                    Common.showToast(mActivity, "暂无数据");
                } else {
                    infos.addAll(tempList);
                }
                break;
            case refreshLoad:
                //如果是刷新加载
                reSetPage();
                infos.clear();
                infos.addAll(tempList);
                break;
            case moreLoad:
                infos.addAll(tempList);
                break;
            case searchLoad:
                //查询加载
                infos.clear();
                infos.addAll(tempList);
                reSetPage();
                break;
        }
        Message message = Message.obtain();
        message.what = 0x12;
        handler.sendMessage(message);
    }

    /**
     * 重置页数
     */
    private void reSetPage() {
        page = 1;
    }

    public void back(View view) {
        finish();
    }

    class TrackListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return infos.size();
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int i, View convertView, ViewGroup viewGroup) {

            View view;
            ViewHolder holder;
            if (convertView != null) {
                view = convertView;
                holder = (ViewHolder) view.getTag();
            } else {
                holder = new ViewHolder();
                view=View.inflate(mActivity, R.layout.track_list_item, null);
                holder.tv_controlnum = (TextView) view.findViewById(R.id.tv_controlnum);
                holder.tv_trucknum = (TextView) view.findViewById(R.id.tv_trucknum);
                holder.tv_reservenum = (TextView) view.findViewById(R.id.tv_reservenum);
                holder.tv_orderdate = (TextView) view.findViewById(R.id.tv_orderdate);
                holder.iv_track_status = (ImageView) view.findViewById(R.id.iv_track_status);
                view.setTag(holder);
            }
            TrackBean bean = infos.get(i);
            holder.tv_controlnum.setText(bean.getControlNum());
            holder.tv_trucknum.setText(bean.getTruckNum());
            holder.tv_reservenum.setText(bean.getReserveNum());
            holder.tv_orderdate.setText(bean.getOrderDate());
            //设置状态背景
            int controlStatus = bean.getControlStatus();
            switch (controlStatus) {
                case 0:
//                    "状态异常";
                    Common.showToast(mActivity, "状态异常");
                    break;
                case 20:
//                    "装货在途";
                    holder.iv_track_status.setImageResource(R.mipmap.tts_loading_way2); //装货在途icon
                    break;
                case 30:
//                    "到达装货";
                    holder.iv_track_status.setImageResource(R.mipmap.tts_arrived_load2); //到达装货icon
                    break;
                case 40:
//                    "装货完成";
                    holder.iv_track_status.setImageResource(R.mipmap.tts_zhuanghuo_finish2); //装货完成icon
                    break;
                case 50:
//                    "送货在途";
                    holder.iv_track_status.setImageResource(R.mipmap.tts_delivery_way2); //送货在途icon
                    break;
                case 60:
//                    "到达收货";
                    holder.iv_track_status.setImageResource(R.mipmap.tts_arrived_receive2); //到达收货icon
                    break;
                case 99:
//                    "收货完成";
                    holder.iv_track_status.setImageResource(R.mipmap.finished_receive2);
                    break;
            }
            return view;
        }
    }

    static class ViewHolder {
        TextView tv_controlnum;
        TextView tv_trucknum;
        TextView tv_reservenum;
        TextView tv_orderdate;
        ImageView iv_track_status;
    }
}
