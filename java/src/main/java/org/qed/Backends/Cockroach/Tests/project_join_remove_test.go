package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestProjectJoinRemove(t *testing.T) {
	rule := loadRule(t, "ProjectJoinRemove")
	e := newEnv(t)
	left, lc := e.scan("t", 1)
	right, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	joined := e.f.ConstructLeftJoin(left, right, on, &memo.JoinPrivate{})
	before := e.f.ConstructProject(joined, nil, opt.MakeColSet(lc[0]))
	want := e.f.ConstructProject(left, nil, opt.MakeColSet(lc[0]))
	e.expectRewrite(rule, before, want)

	inner := e.f.ConstructInnerJoin(left, right, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(inner, nil, opt.MakeColSet(lc[0])))
	e.expectNoMatch(rule, e.f.ConstructProject(joined, nil, opt.MakeColSet(rc[0])))

	right2, rc2 := e.scan("t", 2)
	wrongOn := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc2[1])))
	wrongJoin := e.f.ConstructLeftJoin(left, right2, wrongOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(wrongJoin, nil, opt.MakeColSet(lc[0])))
}
