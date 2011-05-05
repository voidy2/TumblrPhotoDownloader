package main.scala

import org.apache.http.{HttpResponse,HttpStatus}
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.{HttpPost,HttpGet}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.protocol.HTTP.UTF_8
import org.yaml.snakeyaml._

import java.io.{BufferedReader,InputStreamReader}
import javax.xml.ws.http.HTTPException
import scala.xml._
import scala.actors.Actor._
import java.io._
import java.net._

object Main {
  def main(args:Array[String]) {
    val worker = new TumblrApiWorker
    val config = ConfigUserInformation.read
    val xml = worker.dashboard(Map() ++ config)

    worker.getPhotoUrl(xml).foreach(t => actor {
      val th = Thread.currentThread()
      // スレッドIDと名前を表示
      println("id = " + th.getId() + ", name = " + th.getName())
      worker.download(t)
    })
  }
}

object ConfigUserInformation {
  import collection.mutable._

  val FILE_PATH = "./config.yaml"
  val IMAGE_SAVE_PATH = "./image/"

  def init:HashMap[String,Any] = {
    val console = System.console
    println("Emailとパスワードを設定(初回起動)")
    val email = readLine("email:")
    val password = readLine("password:")
    val param = HashMap(
      "email"->email,
      "password"->password,
      "num"->50,
      "save_image_path"->"./image/"
    )
    val saveDir = param.get("save_image_path").get.toString
    if (new File(saveDir).mkdir)
      println("mkdir: %s".format(saveDir))
    write(param)
    param
  }

  def write(param:HashMap[String,Any]):Unit = {
    import collection.JavaConversions._
    val map:java.util.Map[String,Any] = param
    val options = new DumperOptions
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
    val yaml = new Yaml(options)
    yaml.dump(map, new FileWriter(FILE_PATH))
  }

  def read:HashMap[String,Any] = {
    import collection.JavaConversions._
    new File(FILE_PATH).exists match {
      case true => try {
        val map:HashMap[String,Any] = HashMap()
        val config = new Yaml().load(new FileReader(FILE_PATH))
          .asInstanceOf[java.util.LinkedHashMap[String,Any]]
        config.foreach{t => map.put(t._1,t._2)}
        map
      } catch {
        case e:Exception => init
      }
      case false => init
    }
  }
}

class TumblrApiWorker {
  val API_URL = "http://www.tumblr.com/api/dashboard"

  /** dashboad APIを叩く
   * @param args Postするパラメータ
   * @throws HTTPException ステータスコード200以外が返却された場合
   * @return dashboar APIのXML
   */
  def dashboard(param:Map[String, Any]):Elem = {
    val client = new DefaultHttpClient
    val paramString = param.map { case (k, v) =>
      "%s=%s".format(k, v)
    }.mkString("&")

    val httpPost = new HttpPost(API_URL)
    httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded")
    httpPost.setEntity(new StringEntity(paramString, UTF_8))

    val response = client.execute(httpPost)
    val status = response.getStatusLine.getStatusCode
    try {
      status match {
        case HttpStatus.SC_OK => XML.load(response.getEntity.getContent)
        case _ => throw new HTTPException(status)
      }
    } finally {
      client.getConnectionManager.shutdown
    }
  }

  def getPhotoUrl(xml:Elem):Seq[String] = {
    xml \ "posts" \ "post" collect {
      case node:Node
        if node.attribute("type").get.text == "photo" =>
          node \\ "photo-url"
    } map { t => t.head.text }
  }

  def download(url:String, saveDirectory:String = "./image/", timeout:Int = 10000):Unit = {
    def addExt(filename:String, ext:String):String =
      filename.lastIndexOf(".") match {
        case -1 => "%s.%s".format(filename,ext match {
          case "jpeg" => "jpg"
          case _ => ext
        })
        case _ => filename
      }

    var in: InputStream = null
    try {
      val connection = new URL(url).openConnection
      connection.setConnectTimeout(timeout)
      val ext = connection.getHeaderField("Content-Type")
        .split("/").toList.last
      val filename = addExt(url.split("/").toList.last, ext)
      new File(saveDirectory + filename).exists match {
        case true => {
          println("File exists.")
        }
        case false => {
          println(saveDirectory + filename)
          in = connection.getInputStream
          val buf = Stream.continually{ in.read }
            .takeWhile{ -1 != }.map{ _.byteValue }.toArray
          write(buf, saveDirectory + filename)
        }
      }
    } finally {
      if (in != null) in.close
    }
  }

  def write(src:Array[Byte], fileName:String):Unit = {
    var bw: BufferedOutputStream = null
    try{
      bw = new BufferedOutputStream(new FileOutputStream(fileName))
      bw.write(src)
    } catch {
      case e:FileNotFoundException => {
        println("保存先ディレクトリが存在しません")
      }
    } finally {
      if (bw != null) bw.close
    }
  }
}
