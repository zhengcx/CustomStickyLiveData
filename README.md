# CustomStickyLiveData
LiveData's own sticky feature makes it easy to cause many problems when LiveData is static global, which is annoying.
CustomStickyLiveData can resolve this problem.

自定义一个可以控制是否需要粘性的LiveData，具备LiveData优点的基础上解决LiveData自带粘性的问题。

### 一.使用例子
```java
//一.定义CustomStickyLiveData
public class GlobalDataManager {

    private static CustomStickyLiveData<String> test = new CustomStickyLiveData<>();


    public static CustomStickyLiveData<String> getTestLiveData() {
        return test;
    }
}

//二.先发消息
 GlobalDataManager.getTestLiveData().setValue("发送消息");


//三.再注册观察者

//1.默认的非粘性注册
GlobalDataManager.getTestLiveData().observe(this, new Observer<String>() {
      @Override
      public void onChanged(@Nullable String s) {
           Toast.makeText(MainActivity.this, "第一个观察者为非粘性,所以接收不到注册前发送的消息", Toast.LENGTH_SHORT).show();
            }
        });

//2.粘性注册
 GlobalDataManager.getTestLiveData().observe(this, new Observer<String>() {
       @Override
       public void onChanged(@Nullable String s) {
           Toast.makeText(MainActivity.this, "第二个观察者为粘性,接收到了注册前发送的消息", Toast.LENGTH_SHORT).show();
            }
        }, true);
```

## 二.LiveData原理解析
LiveData是google发布的lifecycle-aware components中的一个组件，除了能实现数据和View的绑定响应之外，它最大的卖点就是具备生命周期感知功能，这使得他具备以下几个优点：
* `解决内存泄漏问题`。由于LiveData会在Activity/Fragment等具有生命周期的lifecycleOwner onDestory的时候自动解绑，所以解决了可能存在的内存泄漏问题。之前我们为了避免这个问题，一般有注册绑定的地方都要解绑，而LiveData利用生命周期感知功能解决了这一问题。
* `解决常见的View空异常`。我们通常在一个异步任务回来后需要更新View，而此时页面可能已经被回收，导致经常会出现View空异常，而LiveData由于具备生命周期感知功能，在界面可见的时候才会进行响应，如界面更新等，如果在界面不可见的时候发起notify，会等到界面可见的时候才进行响应更新。所以就很好的解决了空异常的问题。

LiveData的实现上可以说是`订阅发布模式+生命周期感知`,对于Activity/Fragment等LifecycleOwner来说LiveData是观察者，监听者生命周期，而同时LiveData又是被观察者，我们通过观察LiveData，实现数据和View的关系构建。



![](https://user-gold-cdn.xitu.io/2018/8/26/165755ae4c7d7379?w=520&h=272&f=png&s=20555)

这里特别补充一点我觉得比较重要的，那就是`LiveData是粘性的`,使用EventBus的时候我们知道有一种事件模式是粘性的，特点就是消息可以在observer注册之前发送，当observer注册时，依然可接收到之前发送的这个消息。而LiveData天生就是粘性的，下面会讲解为什么他是粘性的。

`之所以说了解清楚这一点很重要，是因为如果你不清楚这一点，可能导致你的监听被多次响应，比如界面被多次刷新等，这显然是你不是你想要的,特别是当你的LiveData是静态全局的时候，它的粘性特性会引起很多问题，比较烦。`

**为了解决LiveData的粘性问题，我这里自定义实现了一个可以自己控制是否需要粘性的LiveData，github地址：**[CustomStickyLiveData](https://github.com/zhengcx/CustomStickyLiveData)

> 当然如果通过hook的方式也可以比较容易的实现去除LiveData粘性的操作，但是考虑到Android源码的更新可能导致反射的变量找不到的风险，以及反射带来的开销，所以这里没有使用hook的方式去做。

### LiveData的实现原理
>单纯的贴源码，分析源码可能比较枯燥，所以下面就尽量以抛出问题，然后解答的方式来解析LiveData的原理。

#### 1.LiveData是如何做到感知Activity/Fragment的生命周期？

&nbsp;&nbsp;lifecycle-aware compents的核心就是生命周期感知，所以这里简单介绍一下这套东西是如何实现的:
首先Activity/Fragment是LifecycleOwner（26.1.0以上的support包中Activity已经默认实现了LifecycleOwner接口）,内部都会有一个LifecycleRegistry存放生命周期State、Event等。而真正核心的操作是，`每个Activity/Fragment在启动时都会自动添加进来一个Headless Fragment(无界面的Fragment)`，由于添加进来的Fragment与Activity的生命周期是同步的，所以当Activity执行相应生命周期方法的时候，同步的也会执行Headless Fragment的生命周期方法，由于这个这个Headless Fragment对我们开发者来说是隐藏的，它会在执行自己生命周期方法的时候更新Activity的LifecycleRegistry里的生命周期State、Event, 并且notifyStateChanged来通知监听Activity生命周期的观察者。这样就到达了生命周期感知的功能，所以其实是一个隐藏的Headless Fragment来实现了监听者能感知到Activity的生命周期。

>插句题外话，其实`利用这样一个Headless Fragment的原理，我们可以实现很多比较有意思的操作`，既然Headless Fragment的很多方法和Activity都是同步的，那么我们就可以偷偷利用Headless Fragment来接管Activity的方法，举个例子，你是否也因为`startActivityFoeResult()和onActivityForResult()`两个方法分离导致看代码逻辑的时候十分不便，利用Headless Fragment，我们可以封装一个方法，在startActivityForResult()的时候直接传一个callback进来，用Headless Fragment的onActivityForResult()来接管Activity的onActivityForResult(),然后在Headless Fragment的onActivityForResult()里直接调用startActivityForResult()时候传进来的callback，这样实现了startActivityForResult()和onActivityForResult()的逻辑在一起，看代码和写代码时更连贯。

回到问题本身：LiveData是如何做到感知Activity/Fragment的生命周期？
上面已经介绍了生命周期感知的原理了，LiveData监听了Activity/Fragment的生命周期就行了，贴个代码看看：
```java
@MainThread
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<T> observer) {
        ``````
        ``````
        owner.getLifecycle().addObserver(wrapper);
    }
```
这样，每当Activity/Fragment的生命周期发生改变的时候，LiveData中都能收到通知，回调都会被执行。

除去上面说的生命周期感知的，其实LiveData里数据的监听就是普通的订阅发布模式，通过Observe（）来注册观察者，之后数据通过setValue()来更新数据，同时notify所有观察者。实现响应。

所以到这里我们基本上已经知道了生命周期感知的原理，接下来我们就可以来看看LiveData的实现原理了，一图胜千言，`下我把LiveData的源码抽象为一张流程图来展示，下面的其他问题都可以在这张图中找到答案`：

![](https://user-gold-cdn.xitu.io/2018/8/26/165755ba97e7189a?w=786&h=797&f=png&s=52048)

可以看到，在LiveData所依附的Activity/Fragment`生命周期发生改变或者通过setValue()改变LiveData数据的时候都会触发notify`，但是触发后，真正要走到最终的响应（即我们注册进去的onChanged()回调）则中间要经历很多判断条件，这也是为什么LiveData能具有自己那些特点的原因.



#### 2.LiveData为什么可以避免内存泄漏？

&nbsp;&nbsp;通过上面，我们可以知道，`当Activity/Fragment的生命周期发生改变时，LiveData中的监听都会被回调`，所以避免内存泄漏就变得十分简单，可以看上图，当LiveData监听到Activity onDestory时则removeObserve,使自己与观察者自动解绑。这样就避免了内存泄漏。
源码上体现如下：
```java
@Override
        public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
            if (mOwner.getLifecycle().getCurrentState() == DESTROYED) {
                removeObserver(mObserver);
                return;
            }
            activeStateChanged(shouldBeActive());
        }
```



#### 3.LiveData为什么可以解决View空异常问题？

这个问题很简单，看上图，因为LiveData响应（比如更新界面操作View）只会在界面可见的时候，如果当前见面不可见，则会延迟到界面可见的时候再响应，所以自然就不会有View空异常的问题了。

那么LiveData是如何实现:
1. `只在界面可见的时候才响应的`
2. `如果当前界面不可见，则会延迟到界面可见的时候再响应`

关于问题1，因为LiveData是能感知到生命周期的，所以在它回调响应的时候会加一个额外的条件，就是当前的生命周期必须是可见状态的，才会继续执行响应，源码如下：
```java
private void considerNotify(ObserverWrapper observer) {
        //如果界面不可见，则不进行响应
        if (!observer.mActive) {
            return;
        }
        if (!observer.shouldBeActive()) {
            observer.activeStateChanged(false);
            return;
        }
        //如果mVersion不大于mLastVersion,说明数据没有发生变化，则不进行响应
        if (observer.mLastVersion >= mVersion) {
            return;
        }
        observer.mLastVersion = mVersion;
        //noinspection unchecked
        observer.mObserver.onChanged((T) mData);
    }
```
```java
@Override
  boolean shouldBeActive() {
       return mOwner.getLifecycle().getCurrentState().isAtLeast(STARTED);
   }
```
关于问题2，在LiveData中有一个全局变量`mVersion`，而每个observer中有一个变量`mLastVersion`。当我们每次setValue()修改一次LiveData的值的时候，全局的mVersion就会+1，这样mVersion就大于mLastVersion：
```java
@MainThread
    protected void setValue(T value) {
        assertMainThread("setValue");
        mVersion++;
        mData = value;
        dispatchingValue(null);
    }
```
而当界面重新可见的时候，只要判断到mVersion大于mLastVersion，则就会进行响应刷新View，响应后才会更新mLastVersion=mVersion。


#### 4.LiveData为什么是粘性的？

所谓粘性，也就是说消息在订阅之前发布了，订阅之后依然可以接受到这个消息，像EventBus实现粘性的原理是，把发布的粘性事件暂时存在全局的集合里，之后当发生订阅的那一刻，遍历集合，将事件拿出来执行。

而LiveData之所以本身就是粘性的，其实从上面的原理图就可以知道，因为setValue()之后，则LiveData中的mVersion+1,也就标志着数据被改变，此时可能还没有被订阅，比如在上一个页面setValue()，在下一个页面的时候才订阅，此时打开下一页的时候发布的消息依然会被接收到，因为在下一个页面打开时候生命周期方法执行后，也是会被notify的，此时判断到数据有被改变过，并且页面是从不可见变为可见，则符合响应的条件，就会执行响应方法。

解决LiveData粘性的方法上面说了：[CustomStickyLiveData](https://github.com/zhengcx/CustomStickyLiveData)
