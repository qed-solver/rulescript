package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistJoinProjectRight(t *testing.T) {
	rule := loadRule(t, "HoistJoinProjectRight")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 2)
	on := e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(rc[1])),
		e.f.ConstructEq(e.v(rc[1]), e.v(rc[0])),
	)
	swapped := e.f.ConstructProject(r, nil, opt.MakeColSet(rc[1], rc[0]))
	before := e.f.ConstructLeftJoin(l, swapped, on, &memo.JoinPrivate{})
	want := e.f.ConstructProject(
		e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}),
		nil,
		opt.MakeColSet(lc[0], rc[1], rc[0]),
	)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, swapped, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	inOrder := e.f.ConstructProject(r, nil, opt.MakeColSet(rc[0], rc[1]))
	e.expectRewrite(rule, e.f.ConstructLeftJoin(l, inOrder, on, &memo.JoinPrivate{}), want)
}
