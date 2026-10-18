package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestEliminateJoinUnderProjectLeft(t *testing.T) {
	rule := loadRule(t, "EliminateJoinUnderProjectLeft")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	keepLeft := opt.MakeColSet(lc[0])
	before := e.f.ConstructProject(e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}), nil, keepLeft)
	e.expectRewrite(rule, before, e.f.ConstructProject(l, nil, keepLeft))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}), nil, keepLeft))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}), nil, opt.MakeColSet(rc[0])))
	ltOn := e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0])))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructLeftJoin(l, r, ltOn, &memo.JoinPrivate{}), nil, keepLeft))
	l2, lc2 := e.scan("t", 2)
	r2, rc2 := e.scan("t", 2)
	on2 := e.filters(e.f.ConstructEq(e.v(lc2[0]), e.v(rc2[0])))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructLeftJoin(l2, r2, on2, &memo.JoinPrivate{}), nil, opt.MakeColSet(lc2[0])))
}
