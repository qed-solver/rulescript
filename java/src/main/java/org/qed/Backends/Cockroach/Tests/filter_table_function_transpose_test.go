package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestFilterTableFunctionTranspose(t *testing.T) {
	rule := loadRule(t, "FilterTableFunctionTranspose")
	e := newEnv(t)
	s, sc := e.scan("u", 2)
	tv, tc := e.scan("t", 1)
	on := e.f.ConstructEq(e.v(sc[0]), e.v(tc[0]))
	pred := e.f.ConstructLt(e.v(sc[0]), e.v(sc[1]))
	outCols := opt.MakeColSet(sc[0], sc[1])

	join := e.f.ConstructInnerJoin(s, tv, e.filters(on), &memo.JoinPrivate{})
	tfs := e.f.ConstructProject(join, nil, outCols)
	before := e.f.ConstructSelect(tfs, e.filters(pred))
	want := e.f.ConstructProject(
		e.f.ConstructInnerJoin(
			e.f.ConstructSelect(s, e.filters(pred)),
			tv,
			e.filters(on),
			&memo.JoinPrivate{},
		),
		nil,
		outCols,
	)
	e.expectRewrite(rule, before, want)

	left := e.f.ConstructLeftJoin(s, tv, e.filters(on), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructProject(left, nil, outCols), e.filters(pred)))

	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(pred)))

	noJoin := e.f.ConstructProject(s, nil, outCols)
	e.expectNoMatch(rule, e.f.ConstructSelect(noJoin, e.filters(pred)))
}
