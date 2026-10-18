package rulescript_test

import (

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func (e *env) intValues(cols []opt.ColumnID, rows ...[]int) memo.RelExpr {
	values := make(memo.ScalarListExpr, len(rows))
	rowTypes := make([]*types.T, len(cols))
	for i := range rowTypes {
		rowTypes[i] = types.Int
	}
	for i, row := range rows {
		elems := make(memo.ScalarListExpr, len(row))
		for j, n := range row {
			elems[j] = e.num(n)
		}
		values[i] = e.f.ConstructTuple(elems, types.MakeTuple(rowTypes))
	}
	return e.f.ConstructValues(values, &memo.ValuesPrivate{
		Cols: append(opt.ColList{}, cols...), ID: e.f.Metadata().NextUniqueID(),
	})
}

func (e *env) exists(input memo.RelExpr) opt.ScalarExpr {
	col := e.f.Metadata().AddColumn("exists", types.Bool)
	return e.f.ConstructExists(input, &memo.ExistsPrivate{LazyEvalProjectionCol: col})
}

func twoNonNullColumns(e *env) (memo.RelExpr, []opt.ColumnID) {
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	return e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}), []opt.ColumnID{lc[0], rc[0]}
}

func oneColumnUnionAll(e *env) (memo.RelExpr, opt.ColumnID) {
	left, lc := e.scan("u", 1)
	right, rc := e.scan("t", 1)
	out := e.f.Metadata().AddColumn("union", types.Int)
	return e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
		LeftCols: opt.ColList{lc[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{out},
	}), out
}
