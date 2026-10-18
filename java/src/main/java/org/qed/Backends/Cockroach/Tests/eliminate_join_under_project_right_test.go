package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateJoinUnderProjectRight(t *testing.T) {
	rule := loadRule(t, "EliminateJoinUnderProjectRight")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructProject(e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}), nil, opt.MakeColSet(rc[0]))
	want := e.f.ConstructProject(r, nil, opt.MakeColSet(rc[0]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}), nil, opt.MakeColSet(lc[0])))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}), nil, opt.MakeColSet(rc[0])))
	u, uc := e.scan("u", 1)
	mixed := e.filters(e.f.ConstructEq(e.v(uc[0]), e.v(rc[0])))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructInnerJoin(u, r, mixed, &memo.JoinPrivate{}), nil, opt.MakeColSet(rc[0])))
}
