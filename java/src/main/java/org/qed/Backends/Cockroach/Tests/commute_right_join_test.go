package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestCommuteRightJoin(t *testing.T) {
	rule := loadRule(t, "CommuteRightJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructRightJoin(l, r, on, &memo.JoinPrivate{})
	want := e.f.ConstructProject(
		e.f.ConstructLeftJoin(r, l, on, &memo.JoinPrivate{}),
		nil,
		opt.MakeColSet(lc[0], rc[0]),
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}))
	wide, wc := e.scan("t", 2)
	wideOn := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(wc[0])))
	e.expectNoMatch(rule, e.f.ConstructRightJoin(l, wide, wideOn, &memo.JoinPrivate{}))
}
