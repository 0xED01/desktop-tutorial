#!/usr/bin/env python3
"""
Generates Cocos Creator 3.8 project metadata for TrustNothing/:
  * stable .meta files (uuid5 from path) for scripts, audio, json, scene, folders
  * assets/scenes/Main.scene with Canvas + Camera + GameRoot component
  * copies tests/solutions.json -> assets/resources/solutions.json (demo routes)
Re-run safe: UUIDs are deterministic, so the scene's component reference stays valid.
"""
import json, os, uuid, shutil

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
PROJ = os.path.join(ROOT, 'TrustNothing')
ASSETS = os.path.join(PROJ, 'assets')
NS = uuid.UUID('6f1d1c3e-5b2a-4c1e-9a77-7275737430a0')  # project namespace

def uid(rel):
    return str(uuid.uuid5(NS, rel.replace('\\', '/')))

B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
def compress_uuid(u):
    h = u.replace('-', '')
    out = h[:5]
    rest = h[5:]
    for i in range(0, len(rest), 3):
        v = int(rest[i:i + 3], 16)
        out += B64[v >> 6] + B64[v & 63]
    return out

def write_meta(path, meta):
    with open(path + '.meta', 'w', encoding='utf-8') as f:
        json.dump(meta, f, indent=2, ensure_ascii=False)

def meta_for(rel, abspath):
    u = uid(rel)
    if os.path.isdir(abspath):
        ud = {}
        if rel == 'assets/resources':
            ud = {'isBundle': True, 'bundleConfigID': 'default', 'bundleName': 'resources', 'priority': 8}
        return {'ver': '1.2.0', 'importer': 'directory', 'imported': True, 'uuid': u, 'files': [], 'subMetas': {}, 'userData': ud}
    ext = os.path.splitext(abspath)[1]
    if ext == '.ts':
        return {'ver': '4.0.24', 'importer': 'typescript', 'imported': True, 'uuid': u, 'files': [], 'subMetas': {}, 'userData': {}}
    if ext in ('.ogg', '.mp3', '.wav'):
        return {'ver': '1.0.0', 'importer': 'audio-clip', 'imported': True, 'uuid': u, 'files': ['.json', ext], 'subMetas': {}, 'userData': {'downloadMode': 0}}
    if ext == '.json':
        return {'ver': '2.0.1', 'importer': 'json', 'imported': True, 'uuid': u, 'files': ['.json'], 'subMetas': {}, 'userData': {}}
    if ext == '.scene':
        return {'ver': '1.1.50', 'importer': 'scene', 'imported': True, 'uuid': u, 'files': ['.json'], 'subMetas': {}, 'userData': {}}
    return None

def v3(x=0, y=0, z=0): return {'__type__': 'cc.Vec3', 'x': x, 'y': y, 'z': z}
def node(name, parent, children, comps, pos, layer, nid):
    return {
        '__type__': 'cc.Node', '_name': name, '_objFlags': 0, '__editorExtras__': {}, '_parent': parent,
        '_children': children, '_active': True, '_components': comps, '_prefab': None,
        '_lpos': pos, '_lrot': {'__type__': 'cc.Quat', 'x': 0, 'y': 0, 'z': 0, 'w': 1}, '_lscale': v3(1, 1, 1),
        '_mobility': 0, '_layer': layer, '_euler': v3(), '_id': nid,
    }

def make_scene(scene_uuid, gameroot_uuid):
    UI_2D, DEFAULT = 33554432, 1073741824
    objs = []
    objs.append({'__type__': 'cc.SceneAsset', '_name': 'Main', '_objFlags': 0, '__editorExtras__': {}, '_native': '', 'scene': {'__id__': 1}})
    scene = node('Main', None, [{'__id__': 2}], [], v3(), DEFAULT, scene_uuid)
    scene['__type__'] = 'cc.Scene'
    scene['autoReleaseAssets'] = False
    scene['_globals'] = {'__id__': 9}
    objs.append(scene)                                                                                   # 1
    objs.append(node('Canvas', {'__id__': 1}, [{'__id__': 3}], [{'__id__': 5}, {'__id__': 6}, {'__id__': 7}, {'__id__': 8}], v3(640, 360, 0), UI_2D, 'canvasNodeTN01'))  # 2
    objs.append(node('Camera', {'__id__': 2}, [], [{'__id__': 4}], v3(0, 0, 1000), DEFAULT, 'cameraNodeTN01'))  # 3
    objs.append({'__type__': 'cc.Camera', '_name': '', '_objFlags': 0, '__editorExtras__': {}, 'node': {'__id__': 3}, '_enabled': True, '__prefab': None,
                 '_projection': 0, '_priority': 1073741824, '_fov': 45, '_fovAxis': 0, '_orthoHeight': 360, '_near': 1, '_far': 2000,
                 '_color': {'__type__': 'cc.Color', 'r': 32, 'g': 32, 'b': 42, 'a': 255}, '_depth': 1, '_stencil': 0, '_clearFlags': 7,
                 '_rect': {'__type__': 'cc.Rect', 'x': 0, 'y': 0, 'width': 1, 'height': 1}, '_aperture': 19, '_shutter': 7, '_iso': 0,
                 '_screenScale': 1, '_visibility': 41943040, '_targetTexture': None, '_postProcess': None, '_usePostProcess': False,
                 '_cameraType': -1, '_trackingType': 0, '_id': 'cameraCompTN01'})                    # 4
    objs.append({'__type__': 'cc.UITransform', '_name': '', '_objFlags': 0, '__editorExtras__': {}, 'node': {'__id__': 2}, '_enabled': True, '__prefab': None,
                 '_contentSize': {'__type__': 'cc.Size', 'width': 1280, 'height': 720}, '_anchorPoint': {'__type__': 'cc.Vec2', 'x': 0.5, 'y': 0.5}, '_id': 'canvasUITN01'})  # 5
    objs.append({'__type__': 'cc.Canvas', '_name': '', '_objFlags': 0, '__editorExtras__': {}, 'node': {'__id__': 2}, '_enabled': True, '__prefab': None,
                 '_cameraComponent': {'__id__': 4}, '_alignCanvasWithScreen': True, '_id': 'canvasCompTN01'})  # 6
    objs.append({'__type__': 'cc.Widget', '_name': '', '_objFlags': 0, '__editorExtras__': {}, 'node': {'__id__': 2}, '_enabled': True, '__prefab': None,
                 '_alignFlags': 45, '_target': None, '_left': 0, '_right': 0, '_top': 0, '_bottom': 0, '_horizontalCenter': 0, '_verticalCenter': 0,
                 '_isAbsLeft': True, '_isAbsRight': True, '_isAbsTop': True, '_isAbsBottom': True, '_isAbsHorizontalCenter': True, '_isAbsVerticalCenter': True,
                 '_originalWidth': 0, '_originalHeight': 0, '_alignMode': 2, '_lockFlags': 0, '_id': 'canvasWidgetTN01'})  # 7
    objs.append({'__type__': compress_uuid(gameroot_uuid), '_name': '', '_objFlags': 0, '__editorExtras__': {}, 'node': {'__id__': 2}, '_enabled': True, '__prefab': None, '_id': 'gameRootTN01'})  # 8
    objs.append({'__type__': 'cc.SceneGlobals', 'ambient': {'__id__': 10}, 'shadows': {'__id__': 11}, '_skybox': {'__id__': 12}, 'fog': {'__id__': 13}, 'octree': {'__id__': 14}})  # 9
    objs.append({'__type__': 'cc.AmbientInfo'})          # 10
    objs.append({'__type__': 'cc.ShadowsInfo', '_enabled': False})  # 11
    objs.append({'__type__': 'cc.SkyboxInfo', '_enabled': False})   # 12
    objs.append({'__type__': 'cc.FogInfo', '_enabled': False})      # 13
    objs.append({'__type__': 'cc.OctreeInfo', '_enabled': False})   # 14
    return objs

def main():
    # assets
    os.makedirs(os.path.join(ASSETS, 'scenes'), exist_ok=True)
    shutil.copyfile(os.path.join(ROOT, 'tests', 'solutions.json'), os.path.join(ASSETS, 'resources', 'solutions.json'))
    gameroot_rel = 'assets/scripts/cocos/GameRoot.ts'
    scene_rel = 'assets/scenes/Main.scene'
    with open(os.path.join(PROJ, scene_rel), 'w', encoding='utf-8') as f:
        json.dump(make_scene(uid(scene_rel), uid(gameroot_rel)), f, indent=1)
    n = 0
    for dirpath, dirnames, filenames in os.walk(ASSETS):
        rel_dir = os.path.relpath(dirpath, PROJ).replace('\\', '/')
        if rel_dir != 'assets':
            m = meta_for(rel_dir, dirpath)
            if m: write_meta(dirpath, m); n += 1
        for fn in filenames:
            if fn.endswith('.meta'): continue
            ap = os.path.join(dirpath, fn)
            m = meta_for(rel_dir + '/' + fn, ap)
            if m: write_meta(ap, m); n += 1
    # project files
    pkg = {'name': 'TrustNothing', 'uuid': uid('project'), 'creator': {'version': '3.8.6'}, 'description': '别信这关 Trust Nothing — 2D 陷阱平台跳跃游戏'}
    with open(os.path.join(PROJ, 'package.json'), 'w', encoding='utf-8') as f: json.dump(pkg, f, indent=2, ensure_ascii=False)
    settings_dir = os.path.join(PROJ, 'settings', 'v2', 'packages')
    os.makedirs(settings_dir, exist_ok=True)
    project = {'__version__': '1.0.6', 'general': {'designResolution': {'width': 1280, 'height': 720, 'fitWidth': False, 'fitHeight': True}, 'highQuality': False},
               'custom_joint_texture_layouts': [], 'script': {'useDefineForClassFields': True, 'allowDeclareFields': True}}
    with open(os.path.join(settings_dir, 'project.json'), 'w') as f: json.dump(project, f, indent=2)
    builder = {'__version__': '1.3.9', 'bundleConfig': {'custom': {}}, 'textureCompressConfig': {'customConfigs': {}}}
    with open(os.path.join(settings_dir, 'builder.json'), 'w') as f: json.dump(builder, f, indent=2)
    print(f'cocos project metadata ok: {n} meta files, scene uuid {uid(scene_rel)}, GameRoot cid {compress_uuid(uid(gameroot_rel))}')

if __name__ == '__main__':
    main()
