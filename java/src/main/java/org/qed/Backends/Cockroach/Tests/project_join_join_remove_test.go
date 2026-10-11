package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestProjectJoinJoinRemove(t *testing.T) {
	rule := loadRule(t, "ProjectJoinJoinRemove")
	e := newEnv(t)
	t1, tc := e.scan("t", 1)
	t2, tc2 := e.scan("t", 1)
	c, cc := e.scan("nn", 1)
	bottomOn := e.filters(e.f.ConstructEq(e.v(tc[0]), e.v(tc2[0])))
	topOn := e.filters(e.f.ConstructEq(e.v(tc[0]), e.v(cc[0])))
	passthrough := opt.MakeColSet(tc[0], cc[0])

	bottom := e.f.ConstructLeftJoin(t1, t2, bottomOn, &memo.JoinPrivate{})
	top := e.f.ConstructLeftJoin(bottom, c, topOn, &memo.JoinPrivate{})
	before := e.f.ConstructProject(top, nil, passthrough)
	direct := e.f.ConstructLeftJoin(t1, c, topOn, &memo.JoinPrivate{})
	want := e.f.ConstructProject(direct, nil, passthrough)
	e.expectRewrite(rule, before, want)

	innerTop := e.f.ConstructInnerJoin(bottom, c, topOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(innerTop, nil, passthrough))

	innerBottom := e.f.ConstructInnerJoin(t1, t2, bottomOn, &memo.JoinPrivate{})
	innerBottomTop := e.f.ConstructLeftJoin(innerBottom, c, topOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(innerBottomTop, nil, passthrough))

	wrongTopOn := e.filters(e.f.ConstructEq(e.v(tc2[0]), e.v(cc[0])))
	wrongTop := e.f.ConstructLeftJoin(bottom, c, wrongTopOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(wrongTop, nil, passthrough))
}
