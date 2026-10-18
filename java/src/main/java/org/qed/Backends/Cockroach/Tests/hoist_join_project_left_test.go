package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestHoistJoinProjectLeft(t *testing.T) {
	rule := loadRule(t, "HoistJoinProjectLeft")
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	r, rc := e.scan("t", 1)
	swapped := e.f.ConstructProject(l, nil, opt.MakeColSet(lc[0], lc[1]))
	on := e.filters(e.f.ConstructEq(e.v(lc[1]), e.v(rc[0])))
	before := e.f.ConstructInnerJoin(swapped, r, on, &memo.JoinPrivate{})
	want := e.f.ConstructProject(
		e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}),
		nil,
		opt.MakeColSet(lc[0], lc[1], rc[0]),
	)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(swapped, r, on, &memo.JoinPrivate{}))

	dropped := e.f.ConstructProject(l, nil, opt.MakeColSet(lc[1]))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(dropped, r, on, &memo.JoinPrivate{}))
}
