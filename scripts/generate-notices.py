#!/usr/bin/env python3
"""Bundle release dependency licenses and verbatim vendor notices, including nested JARs."""
from pathlib import Path
from html.parser import HTMLParser
import argparse, zipfile, io, hashlib, json, urllib.request, xml.etree.ElementTree as E
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--edition',choices=('community','store'),required=True)
edition=parser.parse_args().edition
root=Path(__file__).resolve().parent.parent;out=root/f'app/src/{edition}/assets/legal';out.mkdir(parents=True,exist_ok=True)
common=root/'app/src/main/assets/legal'
class Plain(HTMLParser):
 def __init__(self):super().__init__();self.parts=[];self.skip=False
 def handle_starttag(self,t,a):
  if t in ('style','script'):self.skip=True
  if t in ('p','br','div','h1','h2','h3','li','pre'):self.parts.append('\n')
 def handle_endtag(self,t):
  if t in ('style','script'):self.skip=False
  if t in ('p','div','h1','h2','h3','li','pre'):self.parts.append('\n')
 def handle_data(self,d):
  if not self.skip:self.parts.append(d)
ns={'m':'http://maven.apache.org/POM/4.0.0'};records=[];blocks={}
for line in (root/f'app/build/reports/{edition}-release-license-inventory.tsv').read_text().splitlines():
 coord,f=line.split('\t');g,a,v=coord.split(':');base=Path.home()/'.gradle/caches/modules-2/files-2.1'/g/a/v
 lic=[]
 for p in base.rglob('*.pom'):
  d=E.parse(p);lic += [dict(name=x.findtext('m:name',namespaces=ns),url=x.findtext('m:url',namespaces=ns)) for x in d.findall('.//m:license',ns)]
 if not lic and coord=='com.google.guava:listenablefuture:1.0':lic=[dict(name='Apache License 2.0',url='https://www.apache.org/licenses/LICENSE-2.0.txt')]
 if not lic:raise SystemExit('Missing license: '+coord)
 records.append(dict(coordinate=coord,licenses=lic))
 def scan(z,label):
  for n in z.namelist():
   if n.endswith('/'):continue
   if n=='classes.jar':scan(zipfile.ZipFile(io.BytesIO(z.read(n))),label+'/classes.jar')
   if any(x in n.lower() for x in ('license','notice','copying')) and not n.endswith('.class'):
    b=z.read(n);key=hashlib.sha256(b).hexdigest()
    if key not in blocks:blocks[key]=dict(data=b,sources=[])
    blocks[key]['sources'].append(label+'/'+n)
 scan(zipfile.ZipFile(f),coord)
text=['HomePanel MR — Third-party software notices\n\nThis application independently implements a client for Home Assistant. No Home Assistant or Immersive Home application source is incorporated.\n\nResolved release dependencies:\n']
for r in records:text.append(r['coordinate']+' — '+', '.join(x['name']+' ('+x['url']+')' for x in r['licenses'])+'\n')
for name,url in [('Apache-2.0.txt','https://www.apache.org/licenses/LICENSE-2.0.txt'),('MPL-2.0.txt','https://www.mozilla.org/media/MPL/2.0/index.815ca599c9df.txt')]:
 p=common/name
 if not p.exists():p.write_bytes(urllib.request.urlopen(url,timeout=30).read())
 text.append('\n\n'+name+'\n'+p.read_text())
for key,r in blocks.items():
 suffix='.html' if r['data'].lstrip().startswith(b'<html') else '.txt';(out/(key[:12]+suffix)).write_bytes(r['data'])
 raw=r['data'].decode('utf-8',errors='replace')
 if suffix=='.html':p=Plain();p.feed(raw);raw=''.join(p.parts)
 text.append('\n\n'+'='*60+'\n'+'\n'.join(r['sources'])+'\n'+'='*60+'\n'+raw)
(out/'THIRD_PARTY_NOTICES.txt').write_text(''.join(text));(root/f'app/build/reports/{edition}-release-dependencies.json').write_text(json.dumps(records,indent=2)+'\n')
print(f'Bundled {len(records)} runtime dependencies, {len(blocks)} unique vendor notices.')
