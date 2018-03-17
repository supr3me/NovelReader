package com.example.newbiechen.ireader.model.remote;

import android.accounts.NetworkErrorException;
import android.text.TextUtils;

import com.example.newbiechen.ireader.model.Node;
import com.example.newbiechen.ireader.model.bean.BillBookBean;
import com.example.newbiechen.ireader.model.bean.BookChapterBean;
import com.example.newbiechen.ireader.model.bean.BookCommentBean;
import com.example.newbiechen.ireader.model.bean.BookDetailBean;
import com.example.newbiechen.ireader.model.bean.BookHelpsBean;
import com.example.newbiechen.ireader.model.bean.BookListBean;
import com.example.newbiechen.ireader.model.bean.BookListDetailBean;
import com.example.newbiechen.ireader.model.bean.BookReviewBean;
import com.example.newbiechen.ireader.model.bean.BookTagBean;
import com.example.newbiechen.ireader.model.bean.ChapterInfoBean;
import com.example.newbiechen.ireader.model.bean.CollBookBean;
import com.example.newbiechen.ireader.model.bean.CommentBean;
import com.example.newbiechen.ireader.model.bean.CommentDetailBean;
import com.example.newbiechen.ireader.model.bean.HelpsDetailBean;
import com.example.newbiechen.ireader.model.bean.HotCommentBean;
import com.example.newbiechen.ireader.model.bean.ReviewDetailBean;
import com.example.newbiechen.ireader.model.bean.SortBookBean;
import com.example.newbiechen.ireader.model.bean.packages.BillboardPackage;
import com.example.newbiechen.ireader.model.bean.packages.BookSortPackage;
import com.example.newbiechen.ireader.model.bean.packages.BookSubSortPackage;
import com.example.newbiechen.ireader.model.bean.packages.SearchBookPackage;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;

/**
 * Created by newbiechen on 17-4-20.
 */

public class NbwRepository {
    private static final String TAG = "NbwRepository";
    private static final String BASE_URL = "http://www.nbw.la";
    private static NbwRepository sInstance;
    private Retrofit mRetrofit;
    private BookApi mBookApi;

    private NbwRepository() {
        mRetrofit = RemoteHelper.getInstance()
                .getRetrofit();

        mBookApi = mRetrofit.create(BookApi.class);
    }

    public static NbwRepository getInstance() {
        if (sInstance == null) {
            synchronized (RemoteHelper.class) {
                if (sInstance == null) {
                    sInstance = new NbwRepository();
                }
            }
        }
        return sInstance;
    }

    public Single<List<CollBookBean>> getRecommendBooks(String gender) {
        return mBookApi.getRecommendBookPackage(gender)
                .map(bean -> bean.getBooks());
    }

    /***************************************书籍详情**********************************************/
    public Observable<BookDetailBean> getBookDetail(String bookId) {
        return Observable.create(emitter -> {
            try {
                Request request = new Request.Builder().url(BASE_URL + bookId).build();
                String html = getResponseBody(RemoteHelper.getInstance().getOkHttpClient(), request);
                Node node = new Node(html);
                BookDetailBean bean = new BookDetailBean();
                bean.setCover(node.src("#btop-info > div > article > div > div.col-xs-2 > img"));
                String temp = node.text("#btop-info > div > article > div > div.col-xs-8 > ul > li:nth-child(1) > h1");
                String[] two = temp.split("作者：");
                bean.setAuthor(two[1]);
                bean.setTitle(two[0]);
                bean.set_id(bookId);
                bean.setCat(node.text("#btop-info > div > article > div > div.col-xs-8 > ul > li:nth-child(2) > a"));
                bean.setUpdated(node.text("#btop-info > div > article > div > div.col-xs-8 > ul > li:nth-child(4)"));
                bean.setLastChapter(node.text("#btop-info > div > article > div > div.col-xs-8 > ul > li:nth-child(5) > a"));
                bean.setRetentionRatio("1024");
                emitter.onNext(bean);
            } catch (Exception e) {
                e.printStackTrace();
                emitter.onError(e);
            }
        });
    }

    public Observable<List<BookChapterBean>> getBookChapters(String bookId) {
        return Observable.create(emitter -> {
            try {
                Request request = new Request.Builder().url(BASE_URL + bookId + "mulu.htm").build();
                String html = getResponseBody(RemoteHelper.getInstance().getOkHttpClient(), request);
                Node node = new Node(html);
                List<Node> nodeList = node.list("#chapters-list > li > a");
                List<BookChapterBean> chapters = new ArrayList<>();
                for (Node node1 : nodeList) {
                    BookChapterBean chapter = new BookChapterBean();
                    chapter.setTitle(node1.text());
                    chapter.setLink(BASE_URL + node1.href());
                    chapters.add(chapter);
                }
                emitter.onNext(chapters);
            } catch (Exception e) {
                e.printStackTrace();
                emitter.onError(e);
            }
        });
    }

    /**
     * 注意这里用的是同步请求
     *
     * @param url
     * @return
     */
    public Flowable<ChapterInfoBean> getChapterInfo(String url) {
//        return Single.create(emitter -> {
//            try {
//                Request request = new Request.Builder().url(url).build();
//                String html = getResponseBody(RemoteHelper.getInstance().getOkHttpClient(), request);
//                Node node = new Node(html);
//                ChapterInfoBean detail = new ChapterInfoBean();
//                detail.setTitle(node.text("#h1 > h1"));
//                String body = node.textWithBr("#txtContent");
//                detail.setBody(body);
//                emitter.onSuccess(detail);
//            } catch (Exception e) {
//                e.printStackTrace();
//                emitter.onError(e);
//            }
//        });
        Request request = new Request.Builder().url(url).build();
        return getFlowResponse(RemoteHelper.getInstance().getOkHttpClient(), request)
                .map(html -> {
                    Node node = new Node(html);
                    ChapterInfoBean detail = new ChapterInfoBean();
                    detail.setTitle(node.text("#h1 > h1"));
                    String body = node.textWithBr("#txtContent");
                    detail.setBody(body);
                    return detail;
                });
    }

    /**
     * 查询书籍
     *
     * @param query:书名|作者名
     * @return
     */
    public Single<List<SearchBookPackage.BooksBean>> getSearchBooks(String query) {
        return Single.create(emitter -> {
            Request request = new Request.Builder().url("http://www.nbw.la/search.htm?keyword=" + URLEncoder.encode(query)).build();
            String html = getResponseBody(RemoteHelper.getInstance().getOkHttpClient(), request);
            Node node = new Node(html);
            List<Node> nodeList = node.list("#novel-list > ul > li");
            List<SearchBookPackage.BooksBean> bookList = new ArrayList<>();
            for (Node node1 : nodeList) {
                SearchBookPackage.BooksBean temp = new SearchBookPackage.BooksBean();
                String name = node1.text("div.col-xs-3 > a");
                if (TextUtils.isEmpty(name)) {
                    continue;
                }
                temp.setTitle(name);
                temp.setCat(node1.text("div.col-xs-1 > i"));
                temp.set_id(node1.href("div.col-xs-3 > a"));
                temp.setLastChapter(node1.text("div.col-xs-4 > a"));
                temp.setAuthor(node1.text("div:nth-child(4)"));
                bookList.add(temp);
            }
            emitter.onSuccess(bookList);
        });
    }

    private static String getResponseBody(OkHttpClient client, Request request) {
        Response response = null;
        try {
            response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                byte[] bodybytes = response.body().bytes();
                String body = new String(bodybytes);
                if (body.indexOf("charset=gb2312") != -1) {
                    body = new String(bodybytes, "GB2312");
                } else {
                    body = new String(bodybytes, "gbk");
                }
                return body;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return null;
    }

    private static Observable<String> getResponse(OkHttpClient client, Request request) {
        return Observable.create(emitter -> {
            Response response;
            response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                byte[] bodybytes = response.body().bytes();
                String body = new String(bodybytes);
                if (body.indexOf("charset=gb2312") != -1) {
                    body = new String(bodybytes, "GB2312");
                } else {
                    body = new String(bodybytes, "gbk");
                }
                emitter.onNext(body);
            } else {
                emitter.onError(new NetworkErrorException());
            }
        });
    }

    private static Flowable<String> getFlowResponse(OkHttpClient client, Request request) {
        return Flowable.create(emitter -> {
            Response response;
            response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                byte[] bodybytes = response.body().bytes();
                String body = new String(bodybytes);
                if (body.indexOf("charset=gb2312") != -1) {
                    body = new String(bodybytes, "GB2312");
                } else {
                    body = new String(bodybytes, "gbk");
                }
                emitter.onNext(body);
            } else {
                emitter.onError(new NetworkErrorException());
            }

        }, BackpressureStrategy.LATEST);
    }

    /***********************************************************************************/


    public Single<List<BookCommentBean>> getBookComment(String block, String sort, int start, int limit, String distillate) {

        return mBookApi.getBookCommentList(block, "all", sort, "all", start + "", limit + "", distillate)
                .map((listBean) -> listBean.getPosts());
    }

    public Single<List<BookHelpsBean>> getBookHelps(String sort, int start, int limit, String distillate) {
        return mBookApi.getBookHelpList("all", sort, start + "", limit + "", distillate)
                .map((listBean) -> listBean.getHelps());
    }

    public Single<List<BookReviewBean>> getBookReviews(String sort, String bookType, int start, int limited, String distillate) {
        return mBookApi.getBookReviewList("all", sort, bookType, start + "", limited + "", distillate)
                .map(listBean -> listBean.getReviews());
    }

    public Single<CommentDetailBean> getCommentDetail(String detailId) {
        return mBookApi.getCommentDetailPackage(detailId)
                .map(bean -> bean.getPost());
    }

    public Single<ReviewDetailBean> getReviewDetail(String detailId) {
        return mBookApi.getReviewDetailPacakge(detailId)
                .map(bean -> bean.getReview());
    }

    public Single<HelpsDetailBean> getHelpsDetail(String detailId) {
        return mBookApi.getHelpsDetailPackage(detailId)
                .map(bean -> bean.getHelp());
    }

    public Single<List<CommentBean>> getBestComments(String detailId) {
        return mBookApi.getBestCommentPackage(detailId)
                .map(bean -> bean.getComments());
    }

    /**
     * 获取的是 综合讨论区的 评论
     *
     * @param detailId
     * @param start
     * @param limit
     * @return
     */
    public Single<List<CommentBean>> getDetailComments(String detailId, int start, int limit) {
        return mBookApi.getCommentPackage(detailId, start + "", limit + "")
                .map(bean -> bean.getComments());
    }

    /**
     * 获取的是 书评区和书荒区的 评论
     *
     * @param detailId
     * @param start
     * @param limit
     * @return
     */
    public Single<List<CommentBean>> getDetailBookComments(String detailId, int start, int limit) {
        return mBookApi.getBookCommentPackage(detailId, start + "", limit + "")
                .map(bean -> bean.getComments());
    }

    /*****************************************************************************/
    /**
     * 获取书籍的分类
     *
     * @return
     */
    public Single<BookSortPackage> getBookSortPackage() {
        return mBookApi.getBookSortPackage();
    }

    /**
     * 获取书籍的子分类
     *
     * @return
     */
    public Single<BookSubSortPackage> getBookSubSortPackage() {
        return mBookApi.getBookSubSortPackage();
    }

    /**
     * 根据分类获取书籍列表
     *
     * @param gender
     * @param type
     * @param major
     * @param minor
     * @param start
     * @param limit
     * @return
     */
    public Single<List<SortBookBean>> getSortBooks(String gender, String type, String major, String minor, int start, int limit) {
        return mBookApi.getSortBookPackage(gender, type, major, minor, start, limit)
                .map(bean -> bean.getBooks());
    }

    /*******************************************************************************/

    /**
     * 排行榜的类型
     *
     * @return
     */
    public Single<BillboardPackage> getBillboardPackage() {
        return mBookApi.getBillboardPackage();
    }

    /**
     * 排行榜的书籍
     *
     * @param billId
     * @return
     */
    public Single<List<BillBookBean>> getBillBooks(String billId) {
        return mBookApi.getBillBookPackage(billId)
                .map(bean -> bean.getRanking().getBooks());
    }

    /***********************************书单*************************************/

    /**
     * 获取书单列表
     *
     * @param duration
     * @param sort
     * @param start
     * @param limit
     * @param tag
     * @param gender
     * @return
     */
    public Single<List<BookListBean>> getBookLists(String duration, String sort,
                                                   int start, int limit,
                                                   String tag, String gender) {
        return mBookApi.getBookListPackage(duration, sort, start + "", limit + "", tag, gender)
                .map(bean -> bean.getBookLists());
    }

    /**
     * 获取书单的标签|类型
     *
     * @return
     */
    public Single<List<BookTagBean>> getBookTags() {
        return mBookApi.getBookTagPackage()
                .map(bean -> bean.getData());
    }

    /**
     * 获取书单的详情
     *
     * @param detailId
     * @return
     */
    public Single<BookListDetailBean> getBookListDetail(String detailId) {
        return mBookApi.getBookListDetailPackage(detailId)
                .map(bean -> bean.getBookList());
    }


    public Single<List<HotCommentBean>> getHotComments(String bookId) {
        return mBookApi.getHotCommnentPackage(bookId)
                .map(bean -> bean.getReviews());
    }

    public Single<List<BookListBean>> getRecommendBookList(String bookId, int limit) {
        return mBookApi.getRecommendBookListPackage(bookId, limit + "")
                .map(bean -> bean.getBooklists());
    }
    /********************************书籍搜索*********************************************/
    /**
     * 搜索热词
     *
     * @return
     */
    public Single<List<String>> getHotWords() {
        return mBookApi.getHotWordPackage()
                .map(bean -> bean.getHotWords());
    }

    /**
     * 搜索关键字
     *
     * @param query
     * @return
     */
    public Single<List<String>> getKeyWords(String query) {
        return mBookApi.getKeyWordPacakge(query)
                .map(bean -> bean.getKeywords());

    }


}
